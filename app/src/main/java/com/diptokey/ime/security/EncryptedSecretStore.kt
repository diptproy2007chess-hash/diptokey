package com.diptokey.ime.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores API keys and other secrets encrypted with Android Keystore-backed
 * AES-256.
 *
 * Caveat (documented in the README): on a compromised device, any app with
 * root or sufficient privileges can read these secrets the same way this
 * app can. EncryptedSharedPreferences protects against casual extraction
 * (e.g. via `adb backup`) but is NOT equivalent to a server-side secret vault.
 *
 * API keys are NEVER logged, NEVER included in crash reports, and NEVER sent
 * to anyone except the configured provider over HTTPS.
 */
class EncryptedSecretStore(context: Context) : SecretProvider {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "encrypted_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun listKeys(prefix: String): List<String> =
        prefs.all.keys.filter { it.startsWith(prefix) }.toList()

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFIX_API_KEY = "api_key_"
        const val PREFIX_PROVIDER = "provider_"
    }
}

/**
 * Abstraction that lets us swap the encrypted store for an in-memory one
 * in unit tests, avoiding the Android Keystore dependency under Robolectric.
 */
interface SecretProvider {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun listKeys(prefix: String = ""): List<String>
    fun clearAll()
}

/** In-memory implementation used by tests. Thread-safe via synchronization. */
class InMemorySecretStore : SecretProvider {
    private val map = mutableMapOf<String, String>()
    private val lock = Any()

    override fun get(key: String): String? = synchronized(lock) { map[key] }
    override fun put(key: String, value: String) { synchronized(lock) { map[key] = value } }
    override fun remove(key: String) { synchronized(lock) { map.remove(key) } }
    override fun listKeys(prefix: String): List<String> = synchronized(lock) {
        map.keys.filter { it.startsWith(prefix) }.toList()
    }
    override fun clearAll() { synchronized(lock) { map.clear() } }
}

