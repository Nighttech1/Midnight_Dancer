package com.midnightdancer.core.data.network

import io.ktor.client.HttpClient

/**
 * Thin wrapper around the shared [HttpClient].
 * Product endpoints are intentionally absent (no secrets / prod URLs in Stage 2).
 */
class ApiClient(
    val httpClient: HttpClient,
    val engineName: String = platformHttpEngineName(),
    val userAgent: String = NetworkConfig.USER_AGENT,
) {
    /** Smoke helper — confirms DI / factory wiring without network I/O. */
    fun isReady(): Boolean = true

    fun close() {
        httpClient.close()
    }
}

/**
 * WebSocket telemetry placeholder for Stage 2+.
 * Real rooms / auth / payloads land in a later ticket — not C-12.
 */
interface TelemetryWebSocketStub {
    /** Always true once [WebSockets] plugin is installed on the client. */
    fun isPluginReady(): Boolean
}

class DefaultTelemetryWebSocketStub(
    private val apiClient: ApiClient,
) : TelemetryWebSocketStub {
    override fun isPluginReady(): Boolean = apiClient.isReady()
}
