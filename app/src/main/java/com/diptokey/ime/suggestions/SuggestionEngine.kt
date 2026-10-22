package com.diptokey.ime.suggestions

import android.content.Context
import com.diptokey.ime.transliteration.BanglaDictionary
import com.diptokey.ime.transliteration.TransliterationEngine

/**
 * Local suggestion engine. Combines:
 *   - English word completion from a small embedded lexicon.
 *   - Bangla transliteration candidates from [TransliterationEngine].
 *   - Next-word prediction based on simple bigram statistics.
 *   - User dictionary (frequency-adjusted, persisted in DataStore).
 *
 * This does NOT call any remote LLM. Every keystroke stays local.
 */
class SuggestionEngine private constructor(private val context: Context) {

    private val transliteration = TransliterationEngine.get()
    private val userDictionary = UserDictionary(context)

    // Tiny built-in English word list for completion tests.
    private val englishLexicon: Set<String> = setOf(
        "the", "and", "for", "you", "are", "was", "but", "not", "can", "all",
        "her", "she", "him", "his", "they", "them", "this", "that", "with", "from",
        "have", "has", "had", "been", "will", "would", "could", "should", "may", "might",
        "hello", "world", "love", "hate", "good", "bad", "great", "small", "big", "fast",
        "happy", "sad", "angry", "calm", "tired", "fresh", "old", "new", "young", "long",
        "open", "close", "start", "stop", "begin", "end", "continue", "return", "make", "do",
        "see", "look", "find", "give", "take", "tell", "ask", "answer", "help", "need",
    )

    // Tiny bigram table for next-word prediction.
    private val bigrams: Map<String, List<String>> = mapOf(
        "i" to listOf("am", "will", "have", "was", "want"),
        "you" to listOf("are", "have", "will", "can", "should"),
        "the" to listOf("best", "world", "same", "way", "time"),
        "hello" to listOf("world", "there", "everyone"),
        "good" to listOf("morning", "evening", "night", "luck", "bye"),
        "thank" to listOf("you"),
        "bangla" to listOf("language", "people", "country"),
        "ami" to listOf("bangladesh", "tumi", "bhalo"),
    )

    @Volatile
    private var lastWord: String = ""

    fun onInputStart(sensitive: Boolean) {
        lastWord = ""
        if (sensitive) userDictionary.onSensitiveField()
    }

    fun onInputEnd() { lastWord = "" }

    fun onExternalTextUpdate(text: String) {
        // Track last word for next-word prediction.
        // Use substringAfterLast with `text` as default, so single-word strings return themselves.
        lastWord = text.trim().substringAfterLast(' ', text.trim()).lowercase()
    }

    fun completeEnglish(prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()
        val p = prefix.lowercase()
        return englishLexicon
            .filter { it.startsWith(p) && it != p }
            .sortedBy { it.length }
            .take(3)
    }

    fun nextWordPredictions(): List<String> {
        if (lastWord.isBlank()) return listOf("I", "the", "you", "hello")
        return (bigrams[lastWord] ?: emptyList()).take(3)
    }

    fun banglaCandidates(latin: String): List<String> =
        transliteration.transliterate(latin)

    fun onWordCommitted(word: String) {
        lastWord = word.lowercase()
        // Learn it (no-op in secure mode).
        userDictionary.maybeLearn(word)
    }

    fun clearLearned() {
        userDictionary.clearAll()
    }

    companion object {
        @Volatile
        private var instance: SuggestionEngine? = null

        fun get(context: Context): SuggestionEngine =
            instance ?: synchronized(this) {
                instance ?: SuggestionEngine(context.applicationContext).also { instance = it }
            }
    }
}
