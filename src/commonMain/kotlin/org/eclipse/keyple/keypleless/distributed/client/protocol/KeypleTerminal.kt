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

package org.eclipse.keyple.keypleless.distributed.client.protocol

import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.eclipse.keyple.keypleless.distributed.client.spi.CardIOException
import org.eclipse.keyple.keypleless.distributed.client.spi.LocalReader
import org.eclipse.keyple.keypleless.distributed.client.spi.ReaderIOException
import org.eclipse.keyple.keypleless.distributed.client.spi.SyncNetworkClient

private const val TAG = "KeypleTerminal"

class KeypleTerminal(
    private val reader: LocalReader,
    private val clientId: String,
    private val networkClient: SyncNetworkClient,
    cardSelectionScenarioJsonString: String = ""
) {
  private var selectionScenario: CardSelectionScenario? = null

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  private val messageProcessor = MessageProcessor(json)

  init {
    parseCardSelectionScenario(cardSelectionScenarioJsonString)
  }

  fun setScanMessage(msg: String) {
    reader.setScanMessage(msg)
  }

  suspend fun waitForCard(): Boolean {
    return reader.waitForCardPresent()
  }

  fun waitForCard(onCard: () -> Unit) {
    reader.startCardDetection { onCard() }
  }

  fun release() {
    reader.release()
  }

  fun setCardSelectionScenarioJsonString(cardSelectionScenarioJsonString: String) {
    parseCardSelectionScenario(cardSelectionScenarioJsonString)
  }

  private fun parseCardSelectionScenario(cardSelectionScenarioJsonString: String) {
    if (cardSelectionScenarioJsonString.isNotEmpty()) {
      this.selectionScenario = null
      try {
        this.selectionScenario = json.decodeFromString(cardSelectionScenarioJsonString)
      } catch (e: Exception) {
        Napier.e(message = "Invalid CardScenario: ${e.message}", tag = TAG, throwable = e)
      }
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  suspend fun <T, R> executeRemoteService(
      serviceId: String,
      inputData: T? = null,
      inputSerializer: KSerializer<T>,
      outputSerializer: KSerializer<R>
  ): KeypleResult<R?> {
    val sessionId = Uuid.random().toString()

    var cardSelectResponses = emptyList<CardSelectionResponse>()
    selectionScenario?.let { cardSelectResponses = processCardSelectionScenario(it) }

    val bodyContent =
        ExecuteRemoteServiceBody(
            serviceId = serviceId,
            inputData = makeInputData(inputData, inputSerializer),
            initialCardContent = makeInitialCardContent(cardSelectResponses),
            initialCardContentClassName =
                if (cardSelectResponses.isEmpty()) null else "java.util.Properties",
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
      var serverResponse = networkClient.sendRequest(request)[0]

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
        serverResponse = networkClient.sendRequest(message)[0]
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

  private fun makeInitialCardContent(
      cardSelectResponses: List<CardSelectionResponse>
  ): JsonElement? {
    return if (cardSelectResponses.isEmpty()) {
      null
    } else {
      json.encodeToJsonElement(
          ProcessedCardSelectionScenario(json.encodeToString(cardSelectResponses)))
    }
  }

  private fun <T> makeInputData(inputData: T?, inputSerializer: KSerializer<T>): JsonElement? {
    if (inputData == null) {
      return null
    }
    return json.encodeToJsonElement(inputSerializer, inputData)
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

  private suspend fun processCardSelectionScenario(
      scenario: CardSelectionScenario
  ): List<CardSelectionResponse> {
    val cardSelectionResponses = mutableListOf<CardSelectionResponse>()
    var error: Error? = null
    val nbIterations = scenario.cardSelectors.size

    for (i in 0 ..< nbIterations) {
      try {
        val cardSelectionResponse =
            processCardSelectionRequest(
                scenario.cardSelectors[i],
                scenario.defaultCardSelections[i].cardSelectionRequest,
                scenario.channelControl)
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
    return if (error == null) cardSelectionResponses else emptyList()
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

  @OptIn(ExperimentalStdlibApi::class)
  private suspend fun processApduRequest(apduRequest: ApduRequest): ApduResponse {
    val apdu = reader.transmitApdu(apduRequest.apdu.hexToByteArray())

    if (apdu.size == 2) {
      messageProcessor.createRequest(apdu, apduRequest)?.let {
        return processApduRequest(it)
      }
    }

    // Default case
    return messageProcessor.makeApduResponse(apdu)
  }

  fun releaseReader() {
    reader.release()
  }
}

class UnexpectedStatusWordException(message: String) : Exception(message)
