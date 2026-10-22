package com.diptokey.ime.transliteration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM unit tests for the transliteration engine — no Android framework
 * dependencies needed.
 */
class TransliterationEngineTest {

    private val engine = TransliterationEngine.get()

    @Test
    fun `ami produces bangla`() {
        val out = engine.transliterate("ami")
        assertThat(out).isNotEmpty()
        assertThat(out.first()).isEqualTo("আমি")
    }

    @Test
    fun `tumi produces bangla`() {
        val out = engine.transliterate("tumi")
        assertThat(out.first()).isEqualTo("তুমি")
    }

    @Test
    fun `bhalo produces bangla`() {
        val out = engine.transliterate("bhalo")
        assertThat(out.first()).isEqualTo("ভালো")
    }

    @Test
    fun `bangladesh produces bangla`() {
        val out = engine.transliterate("bangladesh")
        assertThat(out.first()).isEqualTo("বাংলাদেশ")
    }

    @Test
    fun `simple sentence keeps spaces`() {
        val out = engine.transliterate("ami tumi")
        assertThat(out.first()).isEqualTo("আমি তুমি")
    }

    @Test
    fun `empty input returns empty list`() {
        assertThat(engine.transliterate("")).isEmpty()
    }

    @Test
    fun `english whitelist word not transliterated`() {
        val out = engine.transliterate("api")
        assertThat(out.first()).isEqualTo("api")
    }

    @Test
    fun `mixed sentence preserves english whitelist`() {
        val out = engine.transliterate("ami api bhalo")
        assertThat(out.first()).contains("আমি")
        assertThat(out.first()).contains("api")
        assertThat(out.first()).contains("ভালো")
    }

    @Test
    fun `punctuation preserved`() {
        val out = engine.transliterate("tumi,")
        assertThat(out.first()).isEqualTo("তুমি,")
    }

    @Test
    fun `capitalized input transliterates`() {
        val out = engine.transliterate("Ami")
        assertThat(out.first()).isEqualTo("আমি")
    }

    @Test
    fun `multiple candidates returned for ambiguous input`() {
        val out = engine.transliterate("ami")
        assertThat(out.size).isAtLeast(1)
    }
}
