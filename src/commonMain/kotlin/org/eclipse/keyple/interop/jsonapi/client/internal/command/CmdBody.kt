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
package org.eclipse.keyple.interop.jsonapi.client.internal.command

import kotlinx.serialization.Serializable
import org.eclipse.keyple.interop.jsonapi.client.internal.Constants.CORE_API_LEVEL

@Serializable
internal data class CmdBody(
    val coreApiLevel: Int = CORE_API_LEVEL,
    val service: String,
)
