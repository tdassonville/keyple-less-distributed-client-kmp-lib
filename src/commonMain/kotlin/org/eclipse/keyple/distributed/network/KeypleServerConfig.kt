package org.eclipse.keyple.distributed.network

import org.eclipse.keyple.distributed.protocol.LogLevel

data class KeypleServerConfig(
    val host: String,
    val port: Int,
    val endpoint: String,
    val logLevel: LogLevel = LogLevel.NONE,
    // TODO: authent
    val basicAuth: String? = null,
) {
    fun baseUrl() = "${host}:${port}"
    fun serviceUrl() = "${baseUrl()}${endpoint}"
}