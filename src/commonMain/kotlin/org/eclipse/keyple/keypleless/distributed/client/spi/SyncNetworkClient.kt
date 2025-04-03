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
package org.eclipse.keyple.keypleless.distributed.client.spi

import kotlin.coroutines.cancellation.CancellationException
import org.eclipse.keyple.keypleless.distributed.client.protocol.MessageDTO

class ServerIOException(override val message: String) : Exception(message)

/**
 * The network transmission abstraction used to communicate with a Keyple server. See
 * [SimpleHttpNetworkClient](https://github.com/calypsonet/keyple-demo-ticketing-reloading-remote/blob/main/client/kmp/composeApp/src/commonMain/kotlin/org/calypsonet/keyple/demo/reload/remote/network/SimpleHttpNetworkClient.kt)
 * for an example implementation using Ktor with HTTP basic-auth. If you have more constrains, for
 * authentication for example, use this interface and implement your own authentication logics.
 */
interface SyncNetworkClient {

  /**
   * Actual method to transmit the MessageDTO payload to the server, and retrieve back the next
   * MessageDTO the server asks us to process. You must throw a ServerIOException in case of errors.
   *
   * @throws ServerIOException
   * @throws CancellationException
   */
  @Throws(ServerIOException::class, CancellationException::class)
  suspend fun sendRequest(message: MessageDTO): List<MessageDTO>
}
