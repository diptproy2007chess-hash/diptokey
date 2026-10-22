package com.diptokey.ime.ai.providers

import com.diptokey.ime.ai.AIProvider
import com.diptokey.ime.ai.AIProviderConfig
import com.diptokey.ime.ai.AIRequest
import com.diptokey.ime.ai.AiCompletion
import com.diptokey.ime.ai.ProviderError
import com.diptokey.ime.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI Chat Completions compatible provider.
 *
 * Works with: OpenAI, OpenRouter, Together, Groq, Anyscale, vLLM, llama.cpp server,
 * LocalAI — anything that implements POST {base_url}/chat/completions.
 *
 * URL MUST be HTTPS (we reject http:// unless the user explicitly opts in —
 * for development only).
 */
class OpenAICompatibleProvider(
    override val config: AIProviderConfig,
    private val secretStore: SecretProvider,
    private val client: OkHttpClient = defaultClient(config),
) : AIProvider {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun complete(request: AIRequest): Result<AiCompletion> = withContext(Dispatchers.IO) {
        try {
            val apiKey = secretStore.get(config.apiKeyAlias)
                ?: return@withContext Result.failure(ProviderError.Auth("No API key stored for ${config.displayName}"))

            val baseUrl = config.baseUrl.trimEnd('/')
            // Ensure HTTPS unless explicitly opted in (http://localhost).
            if (!baseUrl.startsWith("https://") && !isPermittedInsecure(baseUrl)) {
                return@withContext Result.failure(ProviderError.InvalidConfig("Base URL must use HTTPS: $baseUrl"))
            }

            val prompt = request.instruction
            val body = buildJsonObject {
                put("model", config.modelId)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", "You are DiptoKey AI, a helpful writing assistant inside a keyboard.")
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", (request.temperature ?: config.defaultTemperature))
                request.maxTokens?.let { put("max_tokens", it) } ?: put("max_tokens", config.maxTokens)
            }

            val url = "$baseUrl/chat/completions"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
            config.org?.let { reqBuilder.header("OpenAI-Organization", it) }
            // OpenRouter recommends these for analytics; safe no-op for others.
            reqBuilder.header("HTTP-Referer", "https://diptokey.local/app")
            reqBuilder.header("X-Title", "DiptoKey")
            config.extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

            val req = reqBuilder
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, rawBody))
                }
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), rawBody)
                val text = parsed.choices.firstOrNull()?.message?.content?.trim() ?: ""
                if (text.isBlank()) {
                    return@withContext Result.failure(ProviderError.Server("Empty response"))
                }
                Result.success(AiCompletion(text))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(ProviderError.Network("Cannot reach host: ${e.message}"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(ProviderError.Network("Timeout: ${e.message}"))
        } catch (e: javax.net.ssl.SSLException) {
            Result.failure(ProviderError.Network("TLS error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(ProviderError.Unknown(e.message ?: e::class.simpleName ?: "unknown"))
        }
    }

    private fun isPermittedInsecure(url: String): Boolean {
        // Allow http://localhost and http://10.0.2.2 (Android emulator host) for development.
        return url.startsWith("http://localhost") || url.startsWith("http://10.0.2.2")
                || url.startsWith("http://127.0.0.1")
    }

    private fun mapHttpError(code: Int, body: String): ProviderError {
        val tail = body.take(200)
        return when (code) {
            401, 403 -> ProviderError.Auth("Authentication failed ($code): $tail")
            429 -> ProviderError.RateLimit("Rate limited by provider: $tail")
            in 500..599 -> ProviderError.Server("Server error $code: $tail")
            else -> ProviderError.Unknown("HTTP $code: $tail")
        }
    }

    companion object {
        fun defaultClient(config: AIProviderConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.requestTimeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.requestTimeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(
        val message: Message? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    ) {
        @Serializable
        data class Message(
            val role: String? = null,
            val content: String? = null,
        )
    }
}
