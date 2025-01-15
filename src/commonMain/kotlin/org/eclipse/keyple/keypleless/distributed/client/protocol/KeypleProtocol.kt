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
package org.eclipse.keyple.keypleless.distributed.client.protocol

import kotlinx.serialization.Serializable

const val API_LEVEL = 3
const val CORE_API_LEVEL = 2

const val EXECUTE_REMOTE_SERVICE = "EXECUTE_REMOTE_SERVICE"

const val END_REMOTE_SERVICE = "END_REMOTE_SERVICE"

const val RESP = "RESP"

const val IS_CONTACTLESS = "IS_CONTACTLESS"

const val IS_CARD_PRESENT = "IS_CARD_PRESENT"

const val TRANSMIT_CARD_SELECTION_REQUESTS = "TRANSMIT_CARD_SELECTION_REQUESTS"

const val TRANSMIT_CARD_REQUEST = "TRANSMIT_CARD_REQUEST"

@Serializable
data class MessageDTO(
    var apiLevel: Int = API_LEVEL,
    var sessionId: String,
    var action: String,
    var clientNodeId: String,
    var serverNodeId: String? = null,
    var localReaderName: String? = null,
    var remoteReaderName: String? = null,
    var body: String
)

@Serializable
data class ExecuteRemoteServiceBody<T>(
    val coreApiLevel: Int,
    val serviceId: String,
    val isReaderContactless: Boolean = true,
    val inputData: T?
)

@Serializable
data class Error(
    val message: String? = null,
    val code: ErrorCode,
)

@Serializable
enum class ErrorCode {
  READER_COMMUNICATION_ERROR,
  CARD_COMMUNICATION_ERROR,
  CARD_COMMAND_ERROR,
}

@Serializable
data class CmdBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String,
)

@Serializable
data class IsContactlessRespBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String = "IS_CONTACTLESS",
    val result: Boolean?,
    val error: Error? = null
)

@Serializable
data class TransmitCardRequestRespBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String = "TRANSMIT_CARD_REQUEST",
    val result: CardResponse? = null,
    val error: Error? = null
)

@Serializable
data class IsCardPresentRespBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String = "IS_CARD_PRESENT",
    val result: Boolean?,
    val error: Error? = null
)

@Serializable
data class EndRemoteServiceBody<T>(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val outputData: T?,
)

@Serializable
data class TransmitCardSelectionRequestsCmdBody(
    val coreApiLevel: Int,
    val parameters: TransmitCardSelectionRequestsParameters,
)

@Serializable
data class TransmitCardSelectionRequestsParameters(
    val multiSelectionProcessing: MultiSelectionProcessing,
    val channelControl: ChannelControl,
    val cardSelectors: Array<CardSelector>,
    val cardSelectionRequests: Array<CardSelectionRequest>,
)

@Serializable
data class TransmitCardRequestCmdBody(
    val coreApiLevel: Int,
    val parameters: TransmitCardRequestParameters,
)

@Serializable
data class TransmitCardRequestParameters(
    val cardRequest: CardRequest,
    val channelControl: ChannelControl,
)

@Serializable
enum class MultiSelectionProcessing {
  FIRST_MATCH,
  PROCESS_ALL
}

@Serializable
enum class ChannelControl {
  KEEP_OPEN,
  CLOSE_AFTER,
}

@Serializable
data class CardSelector(
    val logicalProtocolName: String? = null,
    val powerOnDataRegex: String? = null,
    val aid: String? = null,
    val fileOccurrence: FileOccurrence,
    val fileControlInformation: FileControlInformation,
)

@Serializable
enum class FileOccurrence {
  FIRST,
  LAST,
  NEXT,
  PREVIOUS
}

@Serializable
enum class FileControlInformation {
  FCI,
  FCP,
  FMD,
  NO_RESPONSE
}

@Serializable
data class CardSelectionRequest(
    val cardRequest: CardRequest? = null,
    val successfulSelectionStatusWords: Array<String>,
)

@Serializable
data class CardRequest(
    val apduRequests: Array<ApduRequest>,
    val stopOnUnsuccessfulStatusWord: Boolean,
)

@Serializable
data class ApduRequest(
    val apdu: String,
    val successfulStatusWords: Array<String>,
    val info: String? = null,
)

@Serializable
data class TransmitCardSelectionRequestsRespBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String = "TRANSMIT_CARD_SELECTION_REQUESTS",
    var result: List<CardSelectionResponse>,
    val error: Error? = null,
)

@Serializable
data class CardSelectionResponse(
    val hasMatched: Boolean,
    val powerOnData: String? = null,
    val selectApplicationResponse: ApduResponse? = null,
    val cardResponse: CardResponse? = null,
)

@Serializable
data class ApduResponse(
    val apdu: String,
    val statusWord: String,
)

@Serializable
data class CardResponse(
    val isLogicalChannelOpen: Boolean,
    val apduResponses: List<ApduResponse>,
)

class ReaderNotFoundException(message: String) : Exception(message)
