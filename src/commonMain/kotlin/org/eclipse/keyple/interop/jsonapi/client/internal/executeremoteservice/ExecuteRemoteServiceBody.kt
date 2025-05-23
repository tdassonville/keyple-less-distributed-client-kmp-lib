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
package org.eclipse.keyple.interop.jsonapi.client.internal.executeremoteservice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ExecuteRemoteServiceBody(
    val coreApiLevel: Int,
    val serviceId: String,
    val isReaderContactless: Boolean = true,
    val inputData: JsonElement?,
    val initialCardContent: JsonElement? = null,
    val initialCardContentClassName: String? = null
)
