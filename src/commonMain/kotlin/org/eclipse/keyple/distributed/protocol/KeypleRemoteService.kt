/* **************************************************************************************
 * Copyright (c) 2024 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
@file:OptIn(ExperimentalStdlibApi::class)

package org.eclipse.keyple.distributed.protocol

import io.github.aakira.napier.Napier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.eclipse.keyple.distributed.network.KeypleServer
import org.eclipse.keyple.distributed.network.KeypleServerConfig
import org.eclipse.keyple.distributed.network.buildHttpClient
import org.eclipse.keyple.keyplelessreaderlib.CardIOException
import org.eclipse.keyple.keyplelessreaderlib.LocalNfcReader
import org.eclipse.keyple.keyplelessreaderlib.MultiplatformReader
import org.eclipse.keyple.keyplelessreaderlib.ReaderIOException
import org.eclipse.keyple.keyplelessreaderlib.UnexpectedStatusWordException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "KeypleRemoteService"

// TODO rename maybe into KeypleAgent?
class KeypleRemoteService(
    localNfcReader: LocalNfcReader,
    val clientId: String,
    config: KeypleServerConfig
) {

  private val reader = MultiplatformReader(localNfcReader)

  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  fun setScanMessage(msg: String) {
    reader.setScanMessage(msg)
  }

  private val server = KeypleServer(config, buildHttpClient(config.logLevel))
  private val messageProcessor = MessageProcessor(json)

  suspend fun waitForCard(): Boolean {
    return reader.waitForCardPresent()
  }

  suspend fun waitForCard(onCard: () -> Unit) {
    reader.startCardDetection { onCard() }
  }

  fun release() {
    reader.release()
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun <T, R> executeRemoteService(
      serviceId: String,
      inputData: T? = null,
      inputSerializer: KSerializer<T>,
      outputSerializer: KSerializer<R>
  ): KeypleResult<R?> {
    val sessionId = Uuid.random().toString()

    val bodyContent =
        ExecuteRemoteServiceBody(
            serviceId = serviceId,
            inputData = inputData?.let { json.encodeToJsonElement(inputSerializer, inputData) },
            coreApiLevel = CORE_API_LEVEL)
    val request =
        MessageDTO(
            apiLevel = API_LEVEL,
            sessionId = sessionId,
            action = EXECUTE_REMOTE_SERVICE,
            clientNodeId = clientId,
            localReaderName = "KeypleMobileNFCReader",
            body = json.encodeToString(bodyContent),
        )

    try {
      var serverResponse = server.transmitRequest(request)

      while (serverResponse.action != END_REMOTE_SERVICE) {
        Napier.d(tag = TAG, message = "Processing action ${serverResponse.action}")
        val command: CmdBody = json.decodeFromString(serverResponse.body)
        val service = command.service
        Napier.d(tag = TAG, message = "Service: $service")

        val deviceAnswer =
            when (service) {
              IS_CONTACTLESS -> messageProcessor.isContactless()
              IS_CARD_PRESENT -> messageProcessor.isCardPresent()
              TRANSMIT_CARD_SELECTION_REQUESTS -> transmitCardSelectionRequests(serverResponse)
              TRANSMIT_CARD_REQUEST -> transmitCardRequest(serverResponse)
              else -> {
                // TODO check what should we do here? throw an Exception?
                ""
              }
            }

        val message =
            MessageDTO(
                sessionId = sessionId,
                clientNodeId = clientId,
                apiLevel = API_LEVEL,
                action = RESP,
                body = deviceAnswer,
            )
        serverResponse = server.transmitRequest(message)
      }

      val jsonElement = json.parseToJsonElement(serverResponse.body)

      val outputData = jsonElement.jsonObject["outputData"]
      outputData?.let {
        return KeypleResult.Success(json.decodeFromJsonElement(outputSerializer, outputData))
      } ?: return KeypleResult.Success(null)
    } catch (ex: Exception) {
      return KeypleResult.Failure(KeypleError(statusCode = -1, message = ex.message!!))
    } finally {
      reader.closePhysicalChannel()
    }
  }

  private suspend fun transmitCardRequest(message: MessageDTO): String {
    val transmitCardRequestsCmdBody: TransmitCardRequestCmdBody =
        json.decodeFromString(message.body)
    val cardRequest = transmitCardRequestsCmdBody.parameters.cardRequest
    var cardResponse: CardResponse? = null

    var error: Error? = null
    try {
      cardResponse =
          processCardRequest(cardRequest, transmitCardRequestsCmdBody.parameters.channelControl)
    } catch (ex: CardIOException) {
      error = Error(code = ErrorCode.CARD_COMMUNICATION_ERROR, message = ex.message)
    } catch (ex: ReaderNotFoundException) {
      error = Error(code = ErrorCode.READER_COMMUNICATION_ERROR, message = ex.message)
    } catch (ex: UnexpectedStatusWordException) {
      error = Error(code = ErrorCode.CARD_COMMAND_ERROR, message = ex.message)
    }

    error?.let {
      return json.encodeToString(TransmitCardRequestRespBody(error = error))
    }
    return json.encodeToString(TransmitCardRequestRespBody(result = cardResponse))
  }

  private suspend fun transmitCardSelectionRequests(message: MessageDTO): String {
    val transmitCardSelectionRequestsCmdBody: TransmitCardSelectionRequestsCmdBody =
        json.decodeFromString(message.body)
    val cardSelectionResponses = mutableListOf<CardSelectionResponse>()
    var error: Error? = null
    val nbIterations = transmitCardSelectionRequestsCmdBody.parameters.cardSelectionRequests.size

    for (i in 0 ..< nbIterations) {
      try {
        val cardSelectionResponse =
            processCardSelectionRequest(
                transmitCardSelectionRequestsCmdBody.parameters.cardSelectors[i],
                transmitCardSelectionRequestsCmdBody.parameters.cardSelectionRequests[i],
                transmitCardSelectionRequestsCmdBody.parameters.channelControl)
        cardSelectionResponses.add(cardSelectionResponse)
        if (cardSelectionResponse.hasMatched) {
          break
        }
      } catch (ex: CardIOException) {
        error = Error(code = ErrorCode.CARD_COMMUNICATION_ERROR, message = ex.message)
        break
      } catch (ex: ReaderNotFoundException) {
        error = Error(code = ErrorCode.READER_COMMUNICATION_ERROR, message = ex.message)
        break
      } catch (ex: UnexpectedStatusWordException) {
        error = Error(code = ErrorCode.CARD_COMMAND_ERROR, message = ex.message)
        break
      }
    }

    val transmitCardSelectionRequestsRespBody =
        if (error == null) {
          TransmitCardSelectionRequestsRespBody(result = cardSelectionResponses)
        } else {
          TransmitCardSelectionRequestsRespBody(error = error, result = emptyList())
        }
    return json.encodeToString(transmitCardSelectionRequestsRespBody)
  }

  private suspend fun processCardSelectionRequest(
      cardSelector: CardSelector,
      cardSelectionRequest: CardSelectionRequest,
      channelControl: ChannelControl
  ): CardSelectionResponse {
    reader.openPhysicalChannel()
    val apduRequest: ApduRequest = messageProcessor.makeApduRequest(cardSelector)
    val selectAppResponse: ApduResponse = processApduRequest(apduRequest)
    var cardResponse: CardResponse? = null

    cardSelectionRequest.cardRequest?.let {
      cardResponse = processCardRequest(cardSelectionRequest.cardRequest, channelControl)
    }

    val cardSelectionResponse =
        CardSelectionResponse(
            hasMatched =
                cardSelectionRequest.successfulSelectionStatusWords.contains(
                    selectAppResponse.statusWord),
            powerOnData = reader.getPowerOnData(),
            selectApplicationResponse = selectAppResponse,
            cardResponse = cardResponse)

    return cardSelectionResponse
  }

  private suspend fun processCardRequest(
      cardRequest: CardRequest,
      channelControl: ChannelControl
  ): CardResponse {
    var isLogicalChannelOpen = true
    val apduResponses = mutableListOf<ApduResponse>()

    for (apduRequest in cardRequest.apduRequests) {
      try {
        val apduResponse = processApduRequest(apduRequest)
        apduResponses.add(apduResponse)

        if (!apduRequest.successfulStatusWords.contains(apduResponse.statusWord)) {
          throw UnexpectedStatusWordException("Unexpected status word: ${apduResponse.statusWord}")
        }
      } catch (e: ReaderIOException) {
        reader.closePhysicalChannel()
        throw e
      } catch (e: CardIOException) {
        reader.closePhysicalChannel()
        throw e
      }
    }

    if (channelControl == ChannelControl.CLOSE_AFTER) {
      reader.closePhysicalChannel()
      isLogicalChannelOpen = false
    }

    return CardResponse(isLogicalChannelOpen = isLogicalChannelOpen, apduResponses = apduResponses)
  }

  private suspend fun processApduRequest(apduRequest: ApduRequest): ApduResponse {
    val apdu = reader.transmitApdu(apduRequest.apdu.hexToByteArray())

    // TODO why is this a special case?
    if (apdu.size == 2) {
      val request = messageProcessor.createRequest(apdu, apduRequest)
      if (request != null) {
        return processApduRequest(request)
      }
    }

    // Default case
    return messageProcessor.makeApduResponse(apdu)
  }

  fun releaseReader() {
    reader.release()
  }
}
