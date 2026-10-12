package com.midnightdancer.core.data.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Android engine: OkHttp (HTTP/2 negotiated by default for HTTPS; see decisions.md).
 */
actual fun platformHttpEngineFactory(): HttpClientEngineFactory<*> = OkHttp

actual fun platformHttpEngineName(): String = "OkHttp"
