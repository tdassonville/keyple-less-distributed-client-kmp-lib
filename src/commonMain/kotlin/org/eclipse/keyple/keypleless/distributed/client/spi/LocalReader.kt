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
  fun name(): String

  /**
   * Used to set the scan instructions to the user, for applicable NFC readers. Main usage is for
   * iOS: the provided msg is displayed in the iOS system-driven NFC popup
   */
  fun setScanMessage(msg: String)

  /** @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.) */
  suspend fun waitForCardPresent(): Boolean

  /** @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.) */
  fun startCardDetection(onCardFound: () -> Unit)

  /**
   * @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.)
   * @throws CardIOException on IO error with the card (card exited the NFC field, etc.)
   */
  suspend fun openPhysicalChannel()

  fun closePhysicalChannel()

  fun getPowerOnData(): String

  /**
   * @throws ReaderIOException on IO error communicating with the reader (USB unplugged, etc.)
   * @throws CardIOException on IO error with the card (card exited the NFC field, etc.)
   */
  suspend fun transmitApdu(commandApdu: ByteArray): ByteArray

  fun release()
}
