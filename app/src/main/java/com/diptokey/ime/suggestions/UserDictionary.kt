package com.diptokey.ime.suggestions

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.userDictionaryStore by preferencesDataStore(name = "user_dictionary")

/**
 * Tiny per-user dictionary backed by DataStore. Stores learned words with
 * frequency counts. Disabled when [sensitiveField] is true.
 *
 * Note: frequencies are read/written synchronously via [runBlocking]. The
 * blocking is acceptable because calls happen on the IME main thread and
 * DataStore reads from disk are very small (max ~10 KB).
 */
class UserDictionary(private val context: Context) {

    @Volatile
    private var sensitiveField: Boolean = false

    fun onSensitiveField() { sensitiveField = true }

    fun maybeLearn(word: String) {
        if (sensitiveField) return
        if (word.length < 2) return
        if (word.any { it.isDigit() }) return
        runBlocking {
            context.userDictionaryStore.edit { prefs ->
                val current = prefs[KEY_WORDS] ?: ""
                val entries = deserialize(current).toMutableMap()
                val count = entries[word] ?: 0
                entries[word] = count + 1
                prefs[KEY_WORDS] = serialize(entries)
            }
        }
    }

    fun allWords(): Map<String, Int> = runBlocking {
        val prefs = context.userDictionaryStore.data.first()
        deserialize(prefs[KEY_WORDS] ?: "")
    }

    fun clearAll() {
        runBlocking {
            context.userDictionaryStore.edit { it.clear() }
        }
    }

    private fun serialize(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun deserialize(s: String): Map<String, Int> {
        if (s.isBlank()) return emptyMap()
        return s.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    companion object {
        private val KEY_WORDS = stringPreferencesKey("learned_words")
    }
}
