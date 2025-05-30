/* **************************************************************************************
 * Copyright (c) 2025 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.interop.jsonapi.client.internal

import kotlin.experimental.or
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.keyple.interop.jsonapi.client.internal.command.ApduRequest
import org.eclipse.keyple.interop.jsonapi.client.internal.command.CardSelector
import org.eclipse.keyple.interop.jsonapi.client.internal.command.FileControlInformation
import org.eclipse.keyple.interop.jsonapi.client.internal.command.FileOccurrence
import org.eclipse.keyple.interop.jsonapi.client.internal.response.ApduResponse
import org.eclipse.keyple.interop.jsonapi.client.internal.response.IsCardPresentRespBody

internal class MessageProcessor(private val json: Json) {

  private companion object {
    private const val SW_6100: Int = 0x6100
    private const val SW_6C00: Int = 0x6C00
    private const val SW1_MASK: Int = 0xFF00
    private const val SW2_MASK: Int = 0x00FF
  }

  fun isCardPresent(): String {
    // Here we always return "card present" as the flow imposed by this plugin makes it mandatory to
    // have a card present to start communicating with the server.
    val resp = IsCardPresentRespBody(result = true)
    return json.encodeToString(resp)
  }

  @OptIn(ExperimentalStdlibApi::class)
  fun makeApduRequest(cardSelector: CardSelector): ApduRequest {
    val aid = cardSelector.aid!!.hexToByteArray(HexFormat.Default)

    val selectApplicationCommand = ByteArray(6 + aid.size)
    selectApplicationCommand[0] = 0x00.toByte() // CLA
    selectApplicationCommand[1] = 0xA4.toByte() // INS
    selectApplicationCommand[2] = 0x04.toByte() // P1: select by name
    // P2: b0,b1 define the File occurrence, b2,b3 define the File control information
    // we use the bitmask defined in the respective enums
    selectApplicationCommand[3] =
        computeSelectApplicationP2(cardSelector.fileOccurrence, cardSelector.fileControlInformation)
    selectApplicationCommand[4] = aid.size.toByte() // Lc
    aid.copyInto(selectApplicationCommand, 5, 0) // data
    selectApplicationCommand[5 + aid.size] = 0x00 // Le

    return ApduRequest(
        apdu = selectApplicationCommand.toHexString(),
        successfulStatusWords = emptyArray(),
    )
  }

  private fun computeSelectApplicationP2(
      fileOccurrence: FileOccurrence,
      fileControlInformation: FileControlInformation
  ): Byte {
    val p2: Byte =
        when (fileOccurrence) {
          FileOccurrence.FIRST -> 0x00
          FileOccurrence.LAST -> 0x01
          FileOccurrence.NEXT -> 0x02
          FileOccurrence.PREVIOUS -> 0x03
        }
    val p2bis: Byte =
        when (fileControlInformation) {
          FileControlInformation.FCI -> 0x00
          FileControlInformation.FCP -> 0x04
          FileControlInformation.FMD -> 0x08
          FileControlInformation.NO_RESPONSE -> 0x0C
        }
    return p2 or p2bis
  }

  @OptIn(ExperimentalStdlibApi::class)
  fun makeApduResponse(apdu: ByteArray): ApduResponse {
    return ApduResponse(
        apdu = apdu.toHexString(),
        statusWord = apdu.copyOfRange(apdu.size - 2, apdu.size).toHexString(),
    )
  }

  @OptIn(ExperimentalStdlibApi::class)
  fun createRequest(apdu: ByteArray, apduRequest: ApduRequest): ApduRequest? {
    val statusWord = getStatusWordAsInt(apdu)

    if (((statusWord and SW1_MASK) == SW_6100)) {
      val getResponseApdu =
          byteArrayOf(
              0x00.toByte(), 0xC0.toByte(), 0x00.toByte(), 0x00.toByte(), apdu[apdu.size - 1])
      return ApduRequest(
          apdu = getResponseApdu.toHexString(),
          successfulStatusWords = emptyArray(),
          info = "Internal Get Response")
    } else if (((statusWord and SW1_MASK) == SW_6C00)) {
      val sw2 = apdu[apdu.size - 1].toHexString()
      val cApdu = apduRequest.apdu.dropLast(2) + sw2
      return ApduRequest(
          apdu = cApdu,
          successfulStatusWords = apduRequest.successfulStatusWords,
          info = apduRequest.info)
    } else {
      return null
    }
  }

  private fun getStatusWordAsInt(apdu: ByteArray): Int {
    return apdu[apdu.size - 2].toInt().shl(8) or apdu[apdu.size - 1].toInt()
  }
}
