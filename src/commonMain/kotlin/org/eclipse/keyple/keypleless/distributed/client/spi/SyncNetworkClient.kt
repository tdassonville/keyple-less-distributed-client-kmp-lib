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

import org.eclipse.keyple.keypleless.distributed.client.protocol.MessageDTO

class ServerIOException(msg: String) : Exception(msg)

/**
 * The network transmission abstraction used to communicate with a Keyple server. See
 * [SimpleHttpNetworkClient](https://github.com/calypsonet/keyple-demo-ticketing-reloading-remote/blob/main/client/kmp/composeApp/src/commonMain/kotlin/org/calypsonet/keyple/demo/reload/remote/network/SimpleHttpNetworkClient.kt)
 * for an example implementation using Ktor with HTTP basic-auth. If you have more constraints, for
 * authentication for example, use this interface and implement your own authentication logics.
 *
 * @since 1.0.0
 */
interface SyncNetworkClient {

  /**
   * Sends the [MessageDTO] payload to the server and waits asynchronously for the next [MessageDTO]
   * the server asks us to process.
   *
   * @param message The request message to be sent to the counterpart.
   * @return A list containing a single [MessageDTO] object.
   * @throws ServerIOException If an I/O error occurs while communicating with the server.
   * @since 1.0.0
   */
  @Throws(ServerIOException::class, kotlin.coroutines.cancellation.CancellationException::class)
  suspend fun sendRequest(message: MessageDTO): List<MessageDTO>
}
