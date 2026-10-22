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
 * Pure-JVM tests for the OpenAI-compatible provider. Uses MockWebServer
 * instead of a real API endpoint.
 */
class OpenAICompatibleProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var secretStore: InMemorySecretStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        secretStore = InMemorySecretStore()
        secretStore.put("test_key_alias", "sk-test-key-123")
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `valid response is parsed into completion`() = runBlocking {
        val mockJson = """
            {
              "id": "chatcmpl-1",
              "object": "chat.completion",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Hello from the mock server!"},
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val cfg = AIProviderConfig(
            id = "p1",
            displayName = "Test",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "test_key_alias",
            modelId = "gpt-4o-mini"
        )
        val provider = OpenAICompatibleProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "Say hello"))

        assertThat(result.isSuccess).isTrue()
        val completion = result.getOrNull()!!
        assertThat(completion.text).isEqualTo("Hello from the mock server!")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer sk-test-key-123")
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("gpt-4o-mini")
    }

    @Test
    fun `401 response is mapped to Auth error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid api key"}"""))
        val cfg = AIProviderConfig(
            id = "p2",
            displayName = "Test",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "test_key_alias",
            modelId = "gpt-4o-mini"
        )
        val provider = OpenAICompatibleProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "anything"))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.Auth::class.java)
    }

    @Test
    fun `429 response is mapped to RateLimit error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limit"}"""))
        val cfg = AIProviderConfig(
            id = "p3",
            displayName = "Test",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = server.url("/").toString().trimEnd('/'),
            apiKeyAlias = "test_key_alias",
            modelId = "gpt-4o-mini"
        )
        val provider = OpenAICompatibleProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "x"))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.RateLimit::class.java)
    }

    @Test
    fun `http url is rejected unless localhost`() = runBlocking {
        val cfg = AIProviderConfig(
            id = "p4",
            displayName = "Insecure",
            type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "http://example.com",
            apiKeyAlias = "test_key_alias",
            modelId = "m"
        )
        val provider = OpenAICompatibleProvider(cfg, secretStore)
        val result = provider.complete(AIRequest(instruction = "x"))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.InvalidConfig::class.java)
    }
}
