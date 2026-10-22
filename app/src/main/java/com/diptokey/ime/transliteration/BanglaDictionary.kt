package com.diptokey.ime.transliteration

/**
 * A simple Bangla phonetic dictionary for offline suggestions / word ranking.
 *
 * Real implementations should load this from an asset file. Here we embed a
 * curated list of common Bangla words for the test suite.
 */
object BanglaDictionary {

    /** Common Bangla words with rough frequency weights (1 = rare, 10 = very common). */
    val words: Map<String, Int> = mapOf(
        "আমি" to 10,
        "তুমি" to 9,
        "আপনি" to 9,
        "আছি" to 8,
        "আছে" to 8,
        "আছেন" to 7,
        "কি" to 10,
        "কী" to 9,
        "না" to 10,
        "হ্যাঁ" to 9,
        "ভালো" to 9,
        "খুব" to 8,
        "একটা" to 7,
        "একটি" to 7,
        "কিছু" to 7,
        "কথা" to 7,
        "দিন" to 7,
        "রাত" to 6,
        "সকাল" to 5,
        "বাংলা" to 9,
        "বাংলাদেশ" to 9,
        "ঢাকা" to 7,
        "ভালোবাসা" to 6,
        "জীবন" to 6,
        "শিক্ষা" to 5,
        "দেশ" to 7,
        "মানুষ" to 7,
        "কাজ" to 7,
        "সময়" to 7,
        "পানি" to 6,
        "খাবার" to 6,
        "বাড়ি" to 7,
        "যাও" to 6,
        "এসো" to 6,
        "কর" to 7,
        "করে" to 8,
        "করা" to 7,
        "হয়" to 9,
        "ছিল" to 7,
        "হবে" to 7,
        "যাবে" to 7,
        "তাহলে" to 6,
        "তখন" to 6,
        "এখন" to 8,
        "পরে" to 7,
    )

    /** Personalized mappings user can add/override. */
    val personalDictionary: MutableMap<String, String> = mutableMapOf()

    fun addPersonalMapping(latin: String, bangla: String) {
        personalDictionary[latin.lowercase()] = bangla
    }

    fun removePersonalMapping(latin: String) {
        personalDictionary.remove(latin.lowercase())
    }
}
