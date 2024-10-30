package org.eclipse.keyple.distributed.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.eclipse.keyple.distributed.protocol.LogLevel

fun buildHttpClient(debugLog: LogLevel): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        if (debugLog != LogLevel.NONE) {
            install(Logging) {
                logger = io.ktor.client.plugins.logging.Logger.SIMPLE
                level = makeKtorLogLevel(debugLog)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 35000
            socketTimeoutMillis = 36000
        }
        expectSuccess = true
        followRedirects = true
        install(HttpCookies)
    }
}

private fun makeKtorLogLevel(log: LogLevel): io.ktor.client.plugins.logging.LogLevel {
    return when (log) {
        LogLevel.DEBUG -> io.ktor.client.plugins.logging.LogLevel.ALL
        LogLevel.INFO -> io.ktor.client.plugins.logging.LogLevel.INFO
        else -> io.ktor.client.plugins.logging.LogLevel.NONE
    }
}