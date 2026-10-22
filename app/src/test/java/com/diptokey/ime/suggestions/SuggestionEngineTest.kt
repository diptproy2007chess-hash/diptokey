package com.diptokey.ime.suggestions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SuggestionEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = SuggestionEngine.get(context)

    @Test
    fun `english completion returns words with prefix`() {
        val suggestions = engine.completeEnglish("hel")
        assertThat(suggestions).contains("hello")
    }

    @Test
    fun `english completion does not return short prefixes`() {
        val suggestions = engine.completeEnglish("h")
        assertThat(suggestions).isEmpty()
    }

    @Test
    fun `next word predictions after common starter words`() {
        engine.onExternalTextUpdate("hello")
        val next = engine.nextWordPredictions()
        assertThat(next).contains("world")
    }

    @Test
    fun `bangla candidates returns transliterations`() {
        val out = engine.banglaCandidates("ami")
        assertThat(out).contains("আমি")
    }

    @Test
    fun `bangla candidates include latin fallback`() {
        val out = engine.banglaCandidates("api")
        assertThat(out).contains("api")
    }

    @Test
    fun `onWordCommitted tracks last word`() {
        engine.onWordCommitted("hello")
        val next = engine.nextWordPredictions()
        assertThat(next).contains("world")
    }
}
