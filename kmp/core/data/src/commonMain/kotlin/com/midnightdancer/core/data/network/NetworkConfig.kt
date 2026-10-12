package com.midnightdancer.core.data.network

/**
 * Shared HTTP client knobs for Stage 2 skeleton (no product base URL / secrets).
 */
data class NetworkConfig(
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 30_000,
    /** When false, Logging plugin stays at [io.ktor.client.plugins.logging.LogLevel.NONE]. */
    val enableLogging: Boolean = false,
    val userAgent: String = USER_AGENT,
) {
    companion object {
        const val USER_AGENT: String = "MidnightDancer/KMP"
    }
}
