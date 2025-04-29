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

sealed class KeypleResult<T> {
  data class Success<T>(val data: T) : KeypleResult<T>()

  data class Failure<T>(val error: KeypleError, val data: T? = null) : KeypleResult<T>()
}

enum class StatusCode(val code: Int) {
  SUCCESS(0),
  UNKNOWN_ERROR(-1),
  TAG_LOST(-2),
  SERVER_ERROR(-3),
  NETWORK_ERROR(-4),
  READER_ERROR(-5),
  INTERNAL_ERROR(-6),
}

data class KeypleError(val statusCode: Int, val message: String)
