package com.diptokey.ime.transliteration

/**
 * Bangla phonetic transliteration rules.
 *
 * Maps Latin (Romanized) letters to Bangla Unicode characters using a
 * rule set inspired by the popular "Bijoy/Phonetic" Bengali input layout.
 *
 * The rules are applied greedily, longest-match-first, with special handling
 * for vowel modifiers (kar) and conjuncts (juktakkhor).
 *
 * Examples:
 *   "ami"      → "আমি"
 *   "tumi"     → "তুমি"
 *   "bhalo"    → "ভালো"
 *   "bangladesh" → "বাংলাদেশ"
 */
object PhoneticRules {

    /**
     * Mapping from Latin sequence → Bangla sequence.
     * Order matters: longest patterns MUST come first.
     */
    val rules: List<Pair<String, String>> = listOf(
        // ----- Multi-letter consonants / conjuncts -----
        "kSh" to "ক্ষ",
        "kkh" to "ক্ষ",
        "Ng" to "ঙ",
        "NG" to "ং",
        "ng" to "ং",
        "jj" to "জ্জ",
        "tt" to "ত্ত",
        "th" to "থ",
        "dh" to "ধ",
        "kh" to "খ",
        "gh" to "ঘ",
        "ch" to "চ",
        "jh" to "ঝ",
        "Th" to "ঠ",
        "Dh" to "ঢ",
        "ph" to "ফ",
        "bh" to "ভ",
        "mh" to "ম্হ",
        "nh" to "ন্হ",
        "sh" to "শ",
        "Sh" to "ষ",
        "rh" to "ৃ",
        "kh" to "খ",

        // ----- Compound vowels / modifiers (longest first) -----
        "OU" to "ৌ",
        "OU" to "ৌ",
        "ou" to "ৌ",
        "OI" to "ৈ",
        "oi" to "ৈ",
        "uu" to "ূ",
        "UU" to "ূ",
        "ii" to "ী",
        "II" to "ী",
        // ----- Single vowels -----
        "a" to "া",   // Note: 'a' as a modifier means "aa-kar", handled contextually
        "A" to "আ",
        "i" to "ি",
        "I" to "ই",
        "u" to "ু",
        "U" to "উ",
        "e" to "ে",
        "E" to "এ",
        "o" to "ো",
        "O" to "ও",
        "0" to "০",
        "1" to "১",
        "2" to "২",
        "3" to "৩",
        "4" to "৪",
        "5" to "৫",
        "6" to "৬",
        "7" to "৭",
        "8" to "৮",
        "9" to "৯",

        // ----- Single consonants -----
        "k" to "ক",
        "K" to "ক",
        "g" to "গ",
        "G" to "গ",
        "c" to "চ",
        "C" to "চ",
        "j" to "জ",
        "J" to "জ",
        "T" to "ট",
        "t" to "ত",
        "D" to "ড",
        "d" to "দ",
        "N" to "ণ",
        "n" to "ন",
        "p" to "প",
        "P" to "প",
        "b" to "ব",
        "B" to "ব",
        "m" to "ম",
        "M" to "ম",
        "y" to "য",
        "Y" to "য",
        "r" to "র",
        "R" to "র",
        "l" to "ল",
        "L" to "ল",
        "v" to "ভ",
        "V" to "ভ",
        "w" to "ও",
        "W" to "ও",
        "s" to "স",
        "S" to "ষ",
        "h" to "হ",
        "H" to "হ",
        "z" to "য",
        "Z" to "য",
        "f" to "ফ",
        "F" to "ফ",
        "q" to "ক",
        "Q" to "ক",
        "x" to "ক্স",
        "X" to "ক্স",
    )

    /**
     * Special-case whole-word mappings — used as shortcuts for words that the
     * pure phonetic engine gets wrong.
     */
    val wordDictionary: Map<String, String> = mapOf(
        "ami" to "আমি",
        "tumi" to "তুমি",
        "apni" to "আপনি",
        "bangladesh" to "বাংলাদেশ",
        "bhalobasha" to "ভালোবাসা",
        "bhalo" to "ভালো",
        "kemon" to "কেমন",
        "achen" to "আছেন",
        "achi" to "আছি",
        "khub" to "খুব",
        "ekta" to "একটা",
        "kicu" to "কিছু",
        "kichu" to "কিছু",
        "kotha" to "কথা",
        " bollo" to " বলল",
        "din" to "দিন",
        "rat" to "রাত",
        "sokal" to "সকাল",
        "doya" to "দয়া",
        "jibon" to "জীবন",
        "shikkha" to "শিক্ষা",
        "desher" to "দেশের",
        "dhaka" to "ঢাকা",
    )

    /** Common English technical words that should NOT be transliterated. */
    val englishWhitelist: Set<String> = setOf(
        "api", "github", "deploy", "openrouter", "ai", "android", "kotlin",
        "android", "http", "https", "url", "json", "xml", "css", "html",
        "js", "ts", "css", "html", "openai", "gemini", "claude",
        "bug", "feature", "fix", "code", "branch", "commit", "merge",
        "open", "ai", "agent", "llm", "model", "tokenizer", "embedding",
    )
}
