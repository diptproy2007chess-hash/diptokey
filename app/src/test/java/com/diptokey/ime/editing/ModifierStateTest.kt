package com.diptokey.ime.editing

import com.diptokey.ime.keyboard.ModifierState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModifierStateTest {

    @Test
    fun `shift toggles off to ones-hot`() {
        val s = ModifierState()
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.OFF)
        s.toggleShift()
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.ONESHOT)
        assertThat(s.isShiftActive).isTrue()
    }

    @Test
    fun `shift ones-hot to lock`() {
        val s = ModifierState()
        s.toggleShift()  // OFF -> ONESHOT
        s.toggleShift()  // ONESHOT -> LOCK
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.LOCK)
        assertThat(s.isShiftActive).isTrue()
    }

    @Test
    fun `shift lock to off`() {
        val s = ModifierState()
        s.toggleShift()
        s.toggleShift()
        s.toggleShift()
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.OFF)
    }

    @Test
    fun `key consumed disarms ones-hot shift`() {
        val s = ModifierState()
        s.toggleShift()
        s.onKeyConsumed()
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.OFF)
    }

    @Test
    fun `key consumed does not disarm lock`() {
        val s = ModifierState()
        s.toggleShift()  // ONESHOT
        s.toggleShift()  // LOCK
        s.onKeyConsumed()
        assertThat(s.shift).isEqualTo(ModifierState.ShiftState.LOCK)
    }

    @Test
    fun `ctrl toggles off to armed`() {
        val s = ModifierState()
        assertThat(s.isCtrlActive).isFalse()
        s.toggleCtrl()
        assertThat(s.isCtrlActive).isTrue()
    }

    @Test
    fun `key consumed disarms armed ctrl`() {
        val s = ModifierState()
        s.toggleCtrl()
        s.onKeyConsumed()
        assertThat(s.isCtrlActive).isFalse()
    }

    @Test
    fun `reset clears both`() {
        val s = ModifierState()
        s.toggleShift()
        s.toggleCtrl()
        s.reset()
        assertThat(s.isShiftActive).isFalse()
        assertThat(s.isCtrlActive).isFalse()
    }
}
