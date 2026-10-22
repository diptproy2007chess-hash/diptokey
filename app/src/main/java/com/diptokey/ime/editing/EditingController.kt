package com.diptokey.ime.editing

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.diptokey.ime.keyboard.ModifierState

/**
 * Handles cursor movement, selection, copy/cut/paste, undo/redo, and arrow keys.
 *
 * Important Android reality:
 *  - Many editors only honour `sendKeyEvent` for cursor movement, not
 *    `setSelection`. We try both where it makes sense.
 *  - Undo / Redo support is inconsistent across apps. We send the proper key
 *    events and gracefully no-op if the editor does nothing.
 *  - Selection extension (Shift+Arrow) is best-effort and depends on the
 *    underlying `InputConnection` exposing enough cursor info.
 */
class EditingController(
    private val ime: android.inputmethodservice.InputMethodService
) {

    /**
     * Shared with the IME & KeyboardView. The IME assigns the same instance
     * so a Shift press on the keyboard is visible here for selection extension.
     */
    var modifierState: ModifierState = ModifierState()

    // ---- Cursor ----

    fun moveCursorLeft(ic: InputConnection?, meta: Int = 0) {
        ic ?: return
        if ((meta and KeyEvent.META_SHIFT_ON) != 0 || modifierState.isShiftActive) {
            extendSelection(ic, -1)
        } else {
            sendArrow(ic, KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }

    fun moveCursorRight(ic: InputConnection?, meta: Int = 0) {
        ic ?: return
        if ((meta and KeyEvent.META_SHIFT_ON) != 0 || modifierState.isShiftActive) {
            extendSelection(ic, +1)
        } else {
            sendArrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    }

    fun moveCursorUp(ic: InputConnection?, meta: Int = 0) {
        ic ?: return
        if (modifierState.isShiftActive) sendArrow(ic, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.META_SHIFT_ON)
        else sendArrow(ic, KeyEvent.KEYCODE_DPAD_UP)
    }

    fun moveCursorDown(ic: InputConnection?, meta: Int = 0) {
        ic ?: return
        if (modifierState.isShiftActive) sendArrow(ic, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.META_SHIFT_ON)
        else sendArrow(ic, KeyEvent.KEYCODE_DPAD_DOWN)
    }

    fun moveCursorRelative(ic: InputConnection?, steps: Int) {
        ic ?: return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val absSteps = kotlin.math.abs(steps)
        // Batch-send a few at a time to avoid spamming the editor.
        var sent = 0
        val perBatch = 8
        while (sent < absSteps) {
            val n = minOf(perBatch, absSteps - sent)
            ic.beginBatchEdit()
            for (i in 0 until n) sendArrow(ic, code, 0, commit = false)
            ic.endBatchEdit()
            sent += n
        }
    }

    fun moveLineStart(ic: InputConnection?) {
        ic ?: return
        sendArrow(ic, KeyEvent.KEYCODE_HOME)
    }

    fun moveLineEnd(ic: InputConnection?) {
        ic ?: return
        sendArrow(ic, KeyEvent.KEYCODE_MOVE_END)
    }

    // ---- Selection / clipboard ops ----

    fun selectAll(ic: InputConnection?) {
        ic ?: return
        // Try the most reliable path first: native editor key event.
        sendKeyEvent(ic, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
    }

    fun copy(ic: InputConnection?) {
        ic ?: return
        sendKeyEvent(ic, KeyEvent.KEYCODE_COPY, 0)
    }

    fun cut(ic: InputConnection?) {
        ic ?: return
        sendKeyEvent(ic, KeyEvent.KEYCODE_CUT, 0)
    }

    fun paste(ic: InputConnection?) {
        ic ?: return
        sendKeyEvent(ic, KeyEvent.KEYCODE_PASTE, 0)
    }

    fun undo(ic: InputConnection?) {
        ic ?: return
        sendKeyEvent(ic, KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
    }

    fun redo(ic: InputConnection?) {
        ic ?: return
        sendKeyEvent(ic, KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON)
    }

    // ---- Internals ----

    private fun extendSelection(ic: InputConnection, direction: Int) {
        // Android's InputConnection doesn't expose a portable "extend selection"
        // API. Best-effort: use the cursor-position approach. If the editor
        // doesn't support it, this is a no-op.
        val before = ic.getTextBeforeCursor(1, 0)
        val after = ic.getTextAfterCursor(1, 0)
        // Heuristic: if there is a selection, anchor won't move; otherwise move cursor.
        val code = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        sendArrow(ic, code, KeyEvent.META_SHIFT_ON)
    }

    private fun sendArrow(ic: InputConnection, code: Int, meta: Int = 0, commit: Boolean = true) {
        val now = android.os.SystemClock.uptimeMillis()
        val ev = KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta)
        if (commit) ic.beginBatchEdit()
        ic.sendKeyEvent(ev)
        ic.sendKeyEvent(KeyEvent.changeAction(ev, KeyEvent.ACTION_UP))
        if (commit) ic.endBatchEdit()
    }

    private fun sendKeyEvent(ic: InputConnection, code: Int, meta: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        val ev = KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta)
        ic.sendKeyEvent(ev)
        ic.sendKeyEvent(KeyEvent.changeAction(ev, KeyEvent.ACTION_UP))
    }
}
