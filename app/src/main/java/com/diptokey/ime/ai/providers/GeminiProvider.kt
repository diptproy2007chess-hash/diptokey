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
 * Google Gemini provider — uses the native Generative Language API format.
 *
 * Endpoint shape:
 *   POST {base}/models/{model}:generateContent?key={API_KEY}
 *
 * Default base: https://generativelanguage.googleapis.com/v1beta
 *
 * Note: Gemini supports an OpenAI-compatible proxy too, but we intentionally
 * use the native format so users get proper Gemini-only behavior (e.g. safety
 * settings, system_instruction).
 */
class GeminiProvider(
    override val config: AIProviderConfig,
    private val secretStore: SecretProvider,
    private val client: OkHttpClient = defaultClient(config),
) : AIProvider {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun complete(request: AIRequest): Result<AiCompletion> = withContext(Dispatchers.IO) {
        try {
            val apiKey = secretStore.get(config.apiKeyAlias)
                ?: return@withContext Result.failure(ProviderError.Auth("No Gemini API key stored"))

            val baseUrl = config.baseUrl.trimEnd('/')
            if (!baseUrl.startsWith("https://") && !isPermittedInsecure(baseUrl)) {
                return@withContext Result.failure(ProviderError.InvalidConfig("Gemini base URL must be HTTPS (localhost allowed for dev)"))
            }

            val model = config.modelId
            val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

            val body = buildJsonObject {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", "You are DiptoKey AI, a helpful writing assistant inside a keyboard." ) })
                    })
                })
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", request.instruction) })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("temperature", request.temperature ?: config.defaultTemperature)
                    put("maxOutputTokens", request.maxTokens ?: config.maxTokens)
                })
            }

            val req = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, rawBody))
                }
                val parsed = json.decodeFromString(GeminiResponse.serializer(), rawBody)
                val text = parsed.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.joinToString("") { it.text ?: "" }
                    ?.trim()
                    ?: ""
                if (text.isBlank()) {
                    val blocked = parsed.promptFeedback?.blockReason
                    return@withContext Result.failure(
                        if (blocked != null) ProviderError.Server("Blocked: $blocked")
                        else ProviderError.Server("Empty response from Gemini")
                    )
                }
                Result.success(AiCompletion(text))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(ProviderError.Network("Cannot reach Gemini host: ${e.message}"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(ProviderError.Network("Timeout: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(ProviderError.Unknown(e.message ?: e::class.simpleName ?: "unknown"))
        }
    }

    private fun isPermittedInsecure(url: String): Boolean {
        return url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://10.0.2.2")
    }

    private fun mapHttpError(code: Int, body: String): ProviderError {
        val tail = body.take(200)
        return when (code) {
            400, 403 -> ProviderError.Auth("Gemini auth/argument error ($code): $tail")
            404 -> ProviderError.InvalidConfig("Model not found: $tail")
            429 -> ProviderError.RateLimit("Gemini rate limit: $tail")
            in 500..599 -> ProviderError.Server("Gemini server error $code: $tail")
            else -> ProviderError.Unknown("Gemini HTTP $code: $tail")
        }
    }

    companion object {
        fun defaultClient(config: AIProviderConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.requestTimeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.requestTimeoutSec.toLong(), TimeUnit.SECONDS)
            .build()

        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    @SerialName("promptFeedback") val promptFeedback: PromptFeedback? = null,
) {
    @Serializable
    data class Candidate(
        val content: Content? = null,
        @SerialName("finishReason") val finishReason: String? = null,
    )

    @Serializable
    data class Content(
        val parts: List<Part> = emptyList(),
        val role: String? = null,
    )

    @Serializable
    data class Part(val text: String? = null)

    @Serializable
    data class PromptFeedback(val blockReason: String? = null)
}
