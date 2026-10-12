package com.midnightdancer.core.data.network

import io.ktor.client.engine.HttpClientEngineFactory

/**
 * Platform engine: Android OkHttp (HTTP/2), iOS Darwin (NSURLSession HTTP/2).
 * See ticket [decisions.md](memory/tickets/C-12-ktor-client/decisions.md).
 */
expect fun platformHttpEngineFactory(): HttpClientEngineFactory<*>

/** Human-readable engine id for smoke / diagnostics (no network). */
expect fun platformHttpEngineName(): String
