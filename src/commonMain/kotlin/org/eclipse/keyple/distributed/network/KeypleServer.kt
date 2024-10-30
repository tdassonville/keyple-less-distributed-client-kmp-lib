package org.eclipse.keyple.distributed.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class ServerIOException(override val message: String) : Exception(message)

class KeypleServer(val config: KeypleServerConfig, val httpClient: HttpClient) {

    var basicAuth: String? = config.basicAuth

    @OptIn(ExperimentalEncodingApi::class)
    suspend inline fun <reified T> transmitRequest(message: T): T {
        return try {
            val json: List<T> = httpClient.post(config.serviceUrl()) {
                headers {
                    append(HttpHeaders.ContentType, "application/json")
                    basicAuth?.let {
                        append(
                            HttpHeaders.Authorization,
                            "Basic " + Base64.encode(basicAuth!!.encodeToByteArray())
                        )
                    }
                }
                setBody(message)
            }.body()
            json[0]
        } catch (e: Exception) {
            throw ServerIOException("Comm error: ${e.message}")
        }
    }
}
