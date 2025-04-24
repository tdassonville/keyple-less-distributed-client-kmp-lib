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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.eclipse.keyple.keypleless.distributed.client.spi.CardIOException
import org.eclipse.keyple.keypleless.distributed.client.spi.LocalReader
import org.eclipse.keyple.keypleless.distributed.client.spi.ReaderIOException
import org.eclipse.keyple.keypleless.distributed.client.spi.ServerIOException
import org.eclipse.keyple.keypleless.distributed.client.spi.SyncNetworkClient

private const val TAG = "KeypleTerminal"

/**
 * KeypleTerminal is the entry point to the Keyple Distributed Client Library. Provided an instance
 * of a NFC reader, and a NetworkClient, this object will handle the Card Selection Scenario if any,
 * connect the NFC Card to the Keyple server and execute the commands sent by the server.
 *
 * Use [waitForCard] for synchronous card detection or [waitForCard] with a callback parameter for
 * asynchronous detection.
 *
 * Then use [executeRemoteService] to start the Keyple transaction.
 *
 * @param cardSelectionScenarioJsonString An optional Card Selection Strategy Json string. See
 *   [Selection JSON Specification here](https://keyple.org/user-guides/non-keyple-client/selection-json-specification/)
 *   to learn more
 * @property reader The NFC reader to use. Usually an instance of
 *   [NFC Reader](https://github.com/eclipse-keyple/keypleless-reader-nfcmobile-kmp-lib)
 * @property clientId A client ID for your Keyple server to identify this remote reader instance.
 * @property networkClient The network client to use. See
 *   [SimpleHttpNetworkClient](https://github.com/calypsonet/keyple-demo-ticketing-reloading-remote/blob/main/client/kmp/composeApp/src/commonMain/kotlin/org/calypsonet/keyple/demo/reload/remote/network/SimpleHttpNetworkClient.kt)
 *   for an example implementation
 */
class KeypleTerminal(
    private val reader: LocalReader,
    private val clientId: String,
    private val networkClient: SyncNetworkClient,
    cardSelectionScenarioJsonString: String = ""
) {
  private var selectionScenario: CardSelectionScenario? = null
  private var lastError: Exception? = null

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  }

  private val messageProcessor = MessageProcessor(json)

  init {
    parseCardSelectionScenario(cardSelectionScenarioJsonString)
  }

  /**
   * Set the scan instructions to the user, for applicable NFC readers. (Displayed on the iOS system
   * "NFC reader" popup)
   *
   * @param msg The message to display, already localized.
   */
  fun setScanMessage(msg: String) {
    reader.setScanMessage(msg)
  }

  /**
   * Suspends until a card is detected.
   *
   * This function suspends the current coroutine and waits until a card is detected in the reader.
   * It provides a coroutine-friendly alternative to synchronous polling or asynchronous callbacks,
   * and should be called from within a coroutine scope.
   *
   * @return `true` if a card was successfully detected and is present.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class)
  suspend fun waitForCard(): Boolean {
    return reader.waitForCardPresent()
  }

  /**
   * Starts monitoring the reader for card detection events asynchronously.
   *
   * When a card is detected in the reader, the provided [onCardFound] callback is invoked. This
   * function does not block the calling thread and is suitable for use in event-driven or UI-based
   * applications.
   *
   * @param onCardFound The callback function to invoke when a card is detected.
   * @throws ReaderIOException If an I/O error occurs while communicating with the reader.
   * @since 1.0.0
   */
  @Throws(ReaderIOException::class)
  fun waitForCard(onCardDetected: () -> Unit) {
    reader.startCardDetection { onCardDetected() }
  }

  /** Stop scanning for NFC cards. Release the reader resources. */
  fun release() {
    reader.release()
  }

  /**
   * Injects the card selection scenario that has been retrieved by your own means (from your server
   * for example).
   *
   * @param cardSelectionScenarioJsonString The card selection scenario as a JSON string.
   */
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
  suspend fun executeRemoteService(
      serviceId: String,
      inputData: String? = null
  ): KeypleResult<String?> {
    val sessionId = Uuid.random().toString()

    var cardSelectResponses = emptyList<CardSelectionResponse>()
    selectionScenario?.let { cardSelectResponses = processCardSelectionScenario(it) }

    val bodyContentStr =
        if (inputData == null) {
          json.encodeToString(
              ExecuteRemoteServiceBody(
                  serviceId = serviceId,
                  inputData = null,
                  initialCardContent = makeInitialCardContent(cardSelectResponses),
                  initialCardContentClassName =
                      if (cardSelectResponses.isEmpty()) null else "java.util.Properties",
                  coreApiLevel = CORE_API_LEVEL))
        } else {
          val bodyJson = buildJsonObject {
            put("isReaderContactless", true)
            put("serviceId", serviceId)
            if (cardSelectResponses.isNotEmpty()) {
              put("initialCardContent", makeInitialCardContent(cardSelectResponses)!!)
              put("initialCardContentClassName", "java.util.Properties")
            }
            put("coreApiLevel", CORE_API_LEVEL)
            put("inputData", json.parseToJsonElement(inputData))
          }
          json.encodeToString(bodyJson)
        }

    val request =
        MessageDTO(
            apiLevel = API_LEVEL,
            sessionId = sessionId,
            action = EXECUTE_REMOTE_SERVICE,
            clientNodeId = clientId,
            localReaderName = "KeypleMobileNFCReader",
            body = bodyContentStr,
        )

    return executeRemoteService(messageDTO = request)
  }

  /**
   * Execute a remote service on the Keyple server. This suspend method will communicate back and
   * forth, over the network with the keyple server, and over NFC with the card. The server drives
   * the transaction, requesting the card to execute APDU commands. APDU responses are sent back to
   * the server, that can process them and decide to send new APDU commands to execute, as many time
   * as needed.
   *
   * @param serviceId A mandatory service identifier (as defined on your Keyple server)
   * @param inputData optionnal extra data your keyple server may need to execute this service. This
   *   is up to your business logic. For example it could contain a payment ID, a transaction ID, a
   *   customer reference, etc...
   * @param inputSerializer if an inputData is provided, you must provide the serializer for it.
   * @param outputSerializer a deserializer used to return you the parsed output data.
   */
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
            localReaderName = reader.getName(),
            body = json.encodeToString(bodyContent),
        )

    return when (val res = executeRemoteService(request)) {
      is KeypleResult.Success<String?> -> {
        val rawStrData = res.data
        rawStrData?.let {
          try {
            val output = json.decodeFromString(outputSerializer, rawStrData)
            return KeypleResult.Success(output)
          } catch (ex: Exception) {
            KeypleResult.Failure(
                KeypleError(statusCode = StatusCode.INTERNAL_ERROR.code, message = ex.message!!))
          }
        } ?: return KeypleResult.Success(null)
      }
      is KeypleResult.Failure<*> -> KeypleResult.Failure(res.error)
    }
  }

  private suspend fun executeRemoteService(
      messageDTO: MessageDTO,
  ): KeypleResult<String?> {
    lastError = null

    try {
      var serverResponse = networkClient.sendRequest(messageDTO)[0]

      while (serverResponse.action != END_REMOTE_SERVICE) {
        Napier.d(tag = TAG, message = "Processing action ${serverResponse.action}")
        val command: CmdBody = json.decodeFromString(serverResponse.body)
        val service = command.service
        Napier.d(tag = TAG, message = "Service: $service")

        val deviceAnswer =
            when (service) {
              IS_CARD_PRESENT -> messageProcessor.isCardPresent()
              TRANSMIT_CARD_SELECTION_REQUESTS -> transmitCardSelectionRequests(serverResponse)
              TRANSMIT_CARD_REQUEST -> transmitCardRequest(serverResponse)
              else -> {
                return KeypleResult.Failure(
                    KeypleError(
                        statusCode = StatusCode.INTERNAL_ERROR.code,
                        message = "Unknown request: $service"))
              }
            }

        val message =
            MessageDTO(
                sessionId = messageDTO.sessionId,
                clientNodeId = clientId,
                apiLevel = API_LEVEL,
                action = RESP,
                body = deviceAnswer,
            )
        serverResponse = networkClient.sendRequest(message)[0]
      }

      // If an error occurred locally, we want to return it to the caller; so we complete the
      // transaction with the server but we ignore subsequent errors, as they are likely to be
      // just noise and only the "root" error is relevant to the user.
      lastError?.let {
        return KeypleResult.Failure(
            KeypleError(statusCode = makeStatusCode(it), message = it.message!!))
      }
      val jsonElement = json.parseToJsonElement(serverResponse.body)

      val outputData = jsonElement.jsonObject["outputData"]
      outputData?.let {
        return KeypleResult.Success(json.encodeToString(outputData))
      } ?: return KeypleResult.Success(null)
    } catch (ex: Exception) {
      lastError?.let {
        // An error occurred previously, but a new error was thrown while we wanted to cleanly
        // finish the current transaction.
        // Let's return the previous error, as it's more probably actionable for the user.
        return KeypleResult.Failure(
            KeypleError(statusCode = makeStatusCode(it), message = it.message!!))
      }
      // No previous error, so we return the current one
      when (ex) {
        is ServerIOException -> {
          return KeypleResult.Failure(
              KeypleError(statusCode = StatusCode.NETWORK_ERROR.code, message = ex.message!!))
        }
        is CardIOException -> {
          return KeypleResult.Failure(
              KeypleError(statusCode = StatusCode.INTERNAL_ERROR.code, message = ex.message!!))
        }
      }
      return KeypleResult.Failure(
          KeypleError(statusCode = StatusCode.UNKNOWN_ERROR.code, message = ex.message!!))
    } finally {
      reader.closePhysicalChannel()
    }
  }

  private fun makeStatusCode(error: Exception): Int {
    return when (error) {
      is CardIOException -> StatusCode.TAG_LOST
      is ReaderIOException -> StatusCode.READER_ERROR
      else -> StatusCode.UNKNOWN_ERROR
    }.code
  }

  private fun saveError(ex: Exception) {
    if (lastError == null) {
      lastError = ex
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
    } catch (ex: ReaderIOException) {
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
      } catch (ex: ReaderIOException) {
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
      } catch (ex: ReaderIOException) {
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
      } catch (e: Exception) {
        if (e !is UnexpectedStatusWordException) {
          saveError(e)
          reader.closePhysicalChannel()
        }
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

    if (apdu.size < 2) {
      throw CardIOException("No response from card")
    }

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
