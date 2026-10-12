package com.midnightdancer.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the shared [HttpClient] with platform engine (OkHttp / Darwin).
 *
 * No product base URL or auth secrets — Stage 2 skeleton only.
 */
fun createHttpClient(config: NetworkConfig = NetworkConfig()): HttpClient =
    HttpClient(platformHttpEngineFactory()) {
        installMidnightNetwork(config)
    }

/**
 * Same plugins as [createHttpClient], but with an injected engine (e.g. MockEngine in tests).
 */
fun createHttpClientWithEngine(
    engine: HttpClientEngine,
    config: NetworkConfig = NetworkConfig(),
): HttpClient =
    HttpClient(engine) {
        installMidnightNetwork(config)
    }

internal fun HttpClientConfig<*>.installMidnightNetwork(config: NetworkConfig) {
    expectSuccess = false

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                prettyPrint = false
            },
        )
    }

    install(Logging) {
        logger =
            object : Logger {
                override fun log(message: String) {
                    println(message)
                }
            }
        level = if (config.enableLogging) LogLevel.HEADERS else LogLevel.NONE
    }

    // Stage 2 stub: plugin installed for later telemetry; no product sockets yet.
    install(WebSockets)

    defaultRequest {
        header(HttpHeaders.UserAgent, config.userAgent)
    }
}
