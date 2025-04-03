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
package org.eclipse.keyple.keypleless.distributed.client.spi

class ReaderIOException(message: String) : Exception(message)

class CardIOException(message: String) : Exception(message)

/**
 * Local NFC reader abstraction. We provide a Kotlin Multiplatform implementation compatible with
 * iOS, Android and JVM based desktops using a PCSC reader (Windows, macOS, linux). You can provide
 * your own version or support different hardware by implementing this interface.
 */
interface LocalReader {
  /** @returns the name of this reader (mostly indicative) */
  fun name(): String

  /**
   * Used to set the scan instructions to the user, for applicable NFC readers. Main usage is for
   * iOS: the provided msg is displayed in the iOS system-driven NFC popup
   */
  fun setScanMessage(msg: String)

  /**
   * Waits (suspends) for a card to be detected
   *
   * @returns True if a card is detected, otherwise false
   * @throws CancellationException
   */
  suspend fun waitForCardPresent(): Boolean

  /**
   * Waits (asynchronously) for a card to be inserted in the reader, then triggers the provided
   * callback.
   *
   * @param onCard The callback that will be called when a card is detected.
   * @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.)
   */
  fun startCardDetection(onCardFound: () -> Unit)

  /**
   * Attempts to open the physical channel (to establish communication with the card).
   *
   * @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.)
   * @throws CardIOException on IO error with the card (card exited the NFC field, etc.)
   */
  suspend fun openPhysicalChannel()

  /**
   * Attempts to close the current physical channel. The physical channel may have been implicitly
   * closed previously by a card withdrawal.
   *
   * @throws ReaderNotFoundException If the communication with the reader has failed.
   */
  fun closePhysicalChannel()

  /**
   * Gets the power-on data. The power-on data is defined as the data retrieved by the reader when
   * the card is inserted.
   *
   * In the case of a contactless reader, the reader decides what this data is. Contactless readers
   * provide a virtual ATR (partially standardized by the PC/SC standard), but other devices can
   * have their own definition, including for example elements from the anti-collision stage of the
   * ISO14443 protocol (ATQA, ATQB, ATS, SAK, etc).
   *
   * These data being variable from one reader to another, they are defined here in string format
   * which can be either a hexadecimal string or any other relevant information.
   *
   * @return a non empty String
   */
  fun getPowerOnData(): String

  /**
   * Transmits an Application Protocol Data Unit (APDU) command to the smart card and receives the
   * response.
   *
   * @param commandApdu: The command APDU to be transmitted.
   * @return The response APDU received from the smart card.
   * @throws ReaderNotFoundException If the communication with the reader has failed.
   * @throws CardIOException If the communication with the card has failed
   */
  suspend fun transmitApdu(commandApdu: ByteArray): ByteArray

  /** Stop scanning for NFC cards. Release the reader resources. */
  fun release()
}
