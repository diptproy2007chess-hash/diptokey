package com.diptokey.ime.transliteration

/**
 * Rule-based English-to-Bangla phonetic transliteration engine.
 *
 * Pipeline:
 *   1. Tokenize input into words (whitespace-separated), preserve punctuation.
 *   2. For each word:
 *      - If it's a personal dictionary mapping, use that.
 *      - If it's in the common-word dictionary, use that.
 *      - If it's in the English whitelist (e.g. "API"), leave as-is.
 *      - Otherwise apply phonetic rules to produce Bangla.
 *   3. Return both the transliterated text AND a list of alternate candidates
 *      (for ambiguous input).
 *
 * This engine is deterministic and offline.
 */
class TransliterationEngine private constructor() {

    fun transliterate(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val tokens = tokenize(input)
        val primary = StringBuilder()
        for (token in tokens) {
            primary.append(transliterateToken(token))
        }
        val result = mutableListOf(primary.toString())
        // Add an alternate: keep latin (useful when user wants to fall back).
        if (input.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            result.add(input)
        }
        // Add a second alternate using the personal dictionary, if available.
        return result.distinct()
    }

    /** Transliterate a single token; preserves punctuation. */
    private fun transliterateToken(token: String): String {
        if (token.isEmpty()) return token
        // Pure punctuation / numbers / non-Latin → pass through.
        if (!token.any { it in 'a'..'z' || it in 'A'..'Z' }) return token

        val lowerToken = token.lowercase()

        // Personal dictionary override.
        BanglaDictionary.personalDictionary[lowerToken]?.let { return it }

        // Common word dictionary.
        PhoneticRules.wordDictionary[lowerToken]?.let { return it }

        // English whitelist (don't transliterate).
        if (PhoneticRules.englishWhitelist.contains(lowerToken)) return token

        // Apply phonetic rules — longest-match-first.
        return applyRules(token)
    }

    private fun applyRules(input: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = input.length
        while (i < n) {
            // Try to match the longest rule.
            var matched = false
            // Find the longest rule that matches starting at i.
            val ruleMatches = PhoneticRules.rules
                .filter { input.startsWith(it.first, i, ignoreCase = true) }
                .sortedByDescending { it.first.length }
            if (ruleMatches.isNotEmpty()) {
                val r = ruleMatches.first()
                sb.append(r.second)
                i += r.first.length
                matched = true
            }
            if (!matched) {
                // No rule matched — keep the character unchanged.
                sb.append(input[i])
                i++
            }
        }
        // Post-process: handle the "a" special case.
        // When 'a' appears as the first char of a word, it should be "আ" (independent vowel),
        // but when it follows a consonant, it's the "া" kar (modifies the consonant).
        return postProcess(sb.toString())
    }

    private fun postProcess(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder()
        for (i in s.indices) {
            val ch = s[i]
            if (ch == 'া' && (i == 0 || isBanglaVowelSign(s[i - 1]) || i == 0)) {
                // If 'a' is at the start, it should be independent vowel আ.
                // The rule maps 'a' to 'া' (aa-kar). At word start, replace with আ.
                if (i == 0) sb.append('আ')
                else sb.append(ch)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun isBanglaVowelSign(c: Char): Boolean = c in 'া'..'ৌ' || c == 'ঁ' || c == 'ং' || c == 'ঃ'

    /**
     * Tokenize: split into whitespace-separated tokens but KEEP whitespace & punctuation.
     */
    private fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in input) {
            if (ch.isWhitespace()) {
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                tokens += ch.toString()
            } else if (ch in wordChars) {
                sb.append(ch)
            } else {
                // punctuation
                if (sb.isNotEmpty()) { tokens += sb.toString(); sb.clear() }
                tokens += ch.toString()
            }
        }
        if (sb.isNotEmpty()) tokens += sb.toString()
        return tokens
    }

    private val wordChars: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toSet()

    companion object {
        @Volatile
        private var instance: TransliterationEngine? = null

        fun get(): TransliterationEngine =
            instance ?: synchronized(this) { instance ?: TransliterationEngine().also { instance = it } }
    }
}
