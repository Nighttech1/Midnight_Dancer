package com.midnightdancer.core.data.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get

class HttpClientSmokeTest : KoinTest {

    @Test
    fun createHttpClientWithMockEngine_isReady_noLiveNetwork() {
        val engine =
            MockEngine { _ ->
                respond(
                    content = """{"ok":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client =
            createHttpClientWithEngine(
                engine = engine,
                config = NetworkConfig(enableLogging = false),
            )
        val api = ApiClient(httpClient = client, engineName = "Mock")
        assertTrue(api.isReady())
        assertEquals(NetworkConfig.USER_AGENT, api.userAgent)
        api.close()
    }

    @Test
    fun networkModule_providesApiClient_singleton() {
        startKoin {
            modules(networkModule(NetworkConfig(enableLogging = false)))
        }
        try {
            val api = get<ApiClient>()
            assertTrue(api.isReady())
            assertEquals(NetworkConfig.USER_AGENT, api.userAgent)
            // Platform engine name is OkHttp (android unit) or Darwin (ios) — non-blank.
            assertTrue(api.engineName.isNotBlank())
            val stub = get<TelemetryWebSocketStub>()
            assertTrue(stub.isPluginReady())
        } finally {
            stopKoin()
        }
    }
}
