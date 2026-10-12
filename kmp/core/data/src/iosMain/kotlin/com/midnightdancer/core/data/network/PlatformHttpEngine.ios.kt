package com.midnightdancer.core.data.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpEngineFactory(): HttpClientEngineFactory<*> = Darwin

actual fun platformHttpEngineName(): String = "Darwin"
