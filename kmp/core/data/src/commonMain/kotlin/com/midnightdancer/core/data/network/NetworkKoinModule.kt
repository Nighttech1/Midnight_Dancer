package com.midnightdancer.core.data.network

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for C-12 Ktor client.
 *
 * [HttpClient] / [ApiClient] are singletons for the app process.
 * Call [ApiClient.close] on process teardown if needed (Stage 2 has no long-lived sockets).
 */
fun networkModule(
    config: NetworkConfig = NetworkConfig(),
): Module =
    module {
        single<HttpClient> {
            createHttpClient(config)
        }
        single {
            ApiClient(httpClient = get())
        }
        single<TelemetryWebSocketStub> {
            DefaultTelemetryWebSocketStub(apiClient = get())
        }
    }
