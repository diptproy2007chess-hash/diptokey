package com.diptokey.ime.fonts

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FontManagerTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val mgr = FontManager(ctx)

    @Test
    fun `listInstalledFonts starts empty`() {
        mgr.restoreDefaults()
        assertThat(mgr.listInstalledFonts()).isEmpty()
    }

    @Test
    fun `restoreDefaults empties the directory`() {
        mgr.restoreDefaults()
        assertThat(mgr.listInstalledFonts()).isEmpty()
    }

    @Test
    fun `importFont rejects oversized file`() {
        val big = ByteArray(FontManager.MAX_FONT_BYTES.toInt() + 1) { 0 }
        val result = mgr.importFont("huge", big.inputStream())
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `importFont rejects invalid ttf content`() {
        val fake = "not a real font file".toByteArray()
        val result = mgr.importFont("fake", fake.inputStream())
        assertThat(result.isFailure).isTrue()
    }
}
