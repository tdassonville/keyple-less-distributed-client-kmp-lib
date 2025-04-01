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
package org.eclipse.keyple.keypleless.distributed.client.protocol

import kotlinx.serialization.Serializable

@Serializable
internal data class DefaultCardSelection(val cardSelectionRequest: CardSelectionRequest)

@Serializable
internal data class CardSelectionScenario(
    val multiSelectionProcessing: MultiSelectionProcessing,
    val channelControl: ChannelControl,
    val cardSelectors: Array<CardSelector>,
    val defaultCardSelections: Array<DefaultCardSelection>
)

@Serializable
internal data class ProcessedCardSelectionScenario(
    val processedCardSelectionScenarioJsonString: String
)
