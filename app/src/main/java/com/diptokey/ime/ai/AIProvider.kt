package com.diptokey.ime.ai

import kotlinx.serialization.Serializable

/**
 * Provider type — affects how requests are serialized.
 */
@Serializable
enum class ProviderType {
    GEMINI,
    OPENAI_COMPATIBLE,
}

@Serializable
data class AIProviderConfig(
    val id: String,
    val displayName: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKeyAlias: String,           // alias key for EncryptedSecretStore
    val modelId: String,
    val defaultTemperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val requestTimeoutSec: Int = 30,
    val extraHeaders: Map<String, String> = emptyMap(),
    val org: String? = null,           // OpenAI organization
    val project: String? = null,       // Gemini project
)

@Serializable
data class AIRequest(
    val instruction: String,
    val sourceText: String? = null,
    val action: AIAction = AIAction.GENERATE,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
)

@Serializable
enum class AIAction(val displayLabel: String) {
    GENERATE("Generate"),
    REWRITE("Rewrite"),
    GRAMMAR("Improve grammar"),
    TRANSLATE("Translate"),
    SUMMARIZE("Summarize"),
    CONTINUE("Continue writing"),
    TONE("Change tone"),
    SHORTER("Make shorter"),
    DETAILED("Make more detailed"),
    EXPLAIN("Explain selected"),
    ANSWER("Answer question"),
}

@Serializable
data class AIResponse(
    val text: String,
    val provider: String,
    val model: String,
    val finishReason: String? = null,
)

sealed class ProviderError(message: String) : Throwable(message) {
    data class Auth(override val message: String) : ProviderError(message)
    data class RateLimit(override val message: String) : ProviderError(message)
    data class Network(override val message: String) : ProviderError(message)
    data class InvalidConfig(override val message: String) : ProviderError(message)
    data class Server(override val message: String) : ProviderError(message)
    data class Unknown(override val message: String) : ProviderError(message)
}

interface AIProvider {
    val config: AIProviderConfig
    suspend fun complete(request: AIRequest): Result<AiCompletion>
}

data class AiCompletion(
    val text: String,
    val raw: Map<String, Any?> = emptyMap(),
)
