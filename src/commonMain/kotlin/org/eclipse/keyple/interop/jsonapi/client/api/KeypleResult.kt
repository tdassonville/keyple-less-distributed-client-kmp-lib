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
package org.eclipse.keyple.interop.jsonapi.client.api

/**
 * KeypleResult wraps the result of a Keyple operation. It can be either a [Success] or a [Failure].
 * In case of a [Failure], we provide the error status and message but also the data provided by the
 * server. It's up to your business logic to decide what to do with it.
 *
 * @param T The type of the data associated with the result, so it can be deserialized for you. It
 *   is a convention between your keyple server and your client app.
 */
sealed class KeypleResult<out T> {

  data class Success<T>(val data: T) : KeypleResult<T>()

  data class Failure<T>(val status: Status, val message: String, val data: T? = null) :
      KeypleResult<T>()
}
