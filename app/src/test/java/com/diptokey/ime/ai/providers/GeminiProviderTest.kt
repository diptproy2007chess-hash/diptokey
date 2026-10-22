package com.diptokey.ime.ai.providers

import com.diptokey.ime.ai.AIProviderConfig
import com.diptokey.ime.ai.AIRequest
import com.diptokey.ime.ai.ProviderError
import com.diptokey.ime.ai.ProviderType
import com.diptokey.ime.security.InMemorySecretStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM tests for the Gemini provider. Uses MockWebServer instead of a
 * real Gemini endpoint.
 */
class GeminiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var secretStore: InMemorySecretStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        secretStore = InMemorySecretStore()
        secretStore.put("gemini_key_alias", "AIzaSyTest")
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `valid gemini response is parsed`() = runBlocking {
        val mockJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [{"text": "Dhaka is the capital of Bangladesh."}],
                    "role": "model"
                  },
                  "finishReason": "STOP"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val cfg = AIProviderConfig(
            id = "g1",
            displayName = "Gemini",
            type = ProviderType.GEMINI,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "gemini_key_alias",
            modelId = "gemini-1.5-flash"
        )
        val provider = GeminiProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "What is the capital of Bangladesh?"))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.text).isEqualTo("Dhaka is the capital of Bangladesh.")

        val req = server.takeRequest()
        // Verify URL path & query.
        assertThat(req.path).contains("gemini-1.5-flash:generateContent")
        assertThat(req.path).contains("key=AIzaSyTest")
        assertThat(req.getHeader("Content-Type")).startsWith("application/json")
        // Ensure Authorization Bearer header is NOT sent to Gemini native API.
        assertThat(req.getHeader("Authorization")).isNull()
    }

    @Test
    fun `gemini 404 invalid model error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error": {"message": "model not found"}}"""))
        val cfg = AIProviderConfig(
            id = "g2",
            displayName = "Gemini",
            type = ProviderType.GEMINI,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "gemini_key_alias",
            modelId = "no-such-model"
        )
        val provider = GeminiProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "x"))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.InvalidConfig::class.java)
    }

    @Test
    fun `gemini 429 rate limit error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error": "rate limit"}"""))
        val cfg = AIProviderConfig(
            id = "g3",
            displayName = "Gemini",
            type = ProviderType.GEMINI,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "gemini_key_alias",
            modelId = "gemini-1.5-flash"
        )
        val provider = GeminiProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "x"))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.RateLimit::class.java)
    }
}
