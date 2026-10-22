package com.diptokey.ime.ai

import android.content.Context
import com.diptokey.ime.security.SecretProvider
import com.diptokey.ime.ai.providers.GeminiProvider
import com.diptokey.ime.ai.providers.OpenAICompatibleProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.providerStore by preferencesDataStore(name = "ai_providers")

/**
 * Orchestrates AI provider configuration, instantiation, and request routing.
 *
 * Stores provider configs (without secrets) in DataStore. Secrets live in
 * [EncryptedSecretStore], keyed by an opaque alias referenced from the config.
 *
 * Provider selection is explicit per request — the user always knows which
 * provider will be hit. No silent routing.
 */
class AIOrchestrator private constructor(
    private val context: Context,
    private val secretStore: SecretProvider,
    val contextManager: ContextManager,
) {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Volatile
    var configs: List<AIProviderConfig> = emptyList()
        private set

    @Volatile
    var defaultProviderId: String? = null

    val activeProviderFlow = MutableStateFlow<AIProviderConfig?>(null)

    init {
        loadFromDisk()
    }

    fun loadFromDisk() = runBlocking {
        val prefs = context.providerStore.data.first()
        val raw = prefs[KEY_PROVIDERS] ?: ""
        configs = if (raw.isBlank()) emptyList() else json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AIProviderConfig.serializer()),
            raw
        )
        defaultProviderId = prefs[KEY_DEFAULT_PROVIDER]
        activeProviderFlow.value = configs.firstOrNull { it.id == defaultProviderId } ?: configs.firstOrNull()
    }

    fun saveProvider(cfg: AIProviderConfig, apiKey: String?) {
        runBlocking {
            if (!apiKey.isNullOrBlank()) {
                secretStore.put(cfg.apiKeyAlias, apiKey.trim())
            }
            configs = (configs.filterNot { it.id == cfg.id } + cfg).sortedBy { it.displayName }
            if (defaultProviderId == null) defaultProviderId = cfg.id
            persist()
        }
    }

    fun deleteProvider(id: String) {
        runBlocking {
            val cfg = configs.firstOrNull { it.id == id }
            if (cfg != null) secretStore.remove(cfg.apiKeyAlias)
            configs = configs.filterNot { it.id == id }
            if (defaultProviderId == id) defaultProviderId = configs.firstOrNull()?.id
            persist()
        }
    }

    fun setDefault(id: String) {
        runBlocking {
            defaultProviderId = id
            persist()
        }
    }

    fun getProvider(id: String): AIProvider? {
        val cfg = configs.firstOrNull { it.id == id } ?: return null
        return when (cfg.type) {
            ProviderType.GEMINI -> GeminiProvider(cfg, secretStore)
            ProviderType.OPENAI_COMPATIBLE -> OpenAICompatibleProvider(cfg, secretStore)
        }
    }

    fun defaultProvider(): AIProvider? = defaultProviderId?.let { getProvider(it) }

    suspend fun execute(request: AIRequest, providerId: String? = null): Result<AiCompletion> {
        val provider = providerId?.let { getProvider(it) } ?: defaultProvider()
            ?: return Result.failure(ProviderError.InvalidConfig("No provider configured. Open AI settings."))
        val prompt = contextManager.buildPrompt(request.instruction, request.sourceText, request.action)
        val req = request.copy(instruction = prompt)
        return provider.complete(req)
    }

    private suspend fun persist() {
        context.providerStore.edit { prefs ->
            prefs[KEY_PROVIDERS] = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(AIProviderConfig.serializer()),
                configs
            )
            prefs[KEY_DEFAULT_PROVIDER] = defaultProviderId ?: ""
        }
        activeProviderFlow.value = configs.firstOrNull { it.id == defaultProviderId } ?: configs.firstOrNull()
    }

    companion object {
        @Volatile
        private var instance: AIOrchestrator? = null

        fun get(
            context: Context,
            secretStore: SecretProvider,
            contextManager: ContextManager,
        ): AIOrchestrator = instance ?: synchronized(this) {
            instance ?: AIOrchestrator(context.applicationContext, secretStore, contextManager).also { instance = it }
        }

        private val KEY_PROVIDERS = stringPreferencesKey("providers_json")
        private val KEY_DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
    }
}
