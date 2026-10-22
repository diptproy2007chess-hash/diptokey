package com.diptokey.ime.keyboard

/**
 * State of Shift / Ctrl modifiers, with one-shot and lock behavior.
 *
 * - Shift has 3 visual states: OFF, ONESHOT, LOCK (caps lock).
 * - Ctrl is one-shot: stays "armed" until next character key, then disarmed.
 * - Caps lock requires a long-press on Shift.
 */
class ModifierState {

    enum class ShiftState { OFF, ONESHOT, LOCK }
    enum class CtrlState { OFF, ARMED }

    @Volatile
    var shift: ShiftState = ShiftState.OFF
        private set

    @Volatile
    var ctrl: CtrlState = CtrlState.OFF
        private set

    val isShiftActive: Boolean get() = shift != ShiftState.OFF
    val isCtrlActive: Boolean get() = ctrl != CtrlState.OFF

    fun toggleShift(): Boolean {
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONESHOT
            ShiftState.ONESHOT -> ShiftState.LOCK
            ShiftState.LOCK -> ShiftState.OFF
        }
        return true
    }

    fun toggleCtrl(): Boolean {
        ctrl = if (ctrl == CtrlState.OFF) CtrlState.ARMED else CtrlState.OFF
        return true
    }

    /**
     * Called after every consuming key event. Disarms ONESHOT shift and ARMED ctrl.
     */
    fun onKeyConsumed() {
        if (shift == ShiftState.ONESHOT) shift = ShiftState.OFF
        if (ctrl == CtrlState.ARMED) ctrl = CtrlState.OFF
    }

    fun reset() {
        shift = ShiftState.OFF
        ctrl = CtrlState.OFF
    }
}
