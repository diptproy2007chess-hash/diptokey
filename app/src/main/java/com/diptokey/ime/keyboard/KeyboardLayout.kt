package com.diptokey.ime.keyboard

import android.view.KeyEvent

/**
 * Immutable description of a single keyboard row and its keys.
 *
 * Keys are not views — they are drawn manually by [KeyboardView] to keep
 * IME rendering fast and lightweight.
 */
data class KeyDef(
    val code: Int = 0,                  // KeyEvent.KEYCODE_* or 0 for a literal char
    val label: String = "",             // Visible label
    val widthWeight: Float = 1.0f,      // Relative width inside the row
    val isModifier: Boolean = false,
    val isFunctional: Boolean = false,  // Functional = backspace, enter, space, arrows
    val isRepeatable: Boolean = false,
    val hint: String = "",              // Optional secondary label (e.g. "?123")
    val toggleMode: KeyToggleMode = KeyToggleMode.NONE
)

enum class KeyToggleMode { NONE, SHIFT, CTRL, EDIT_MODE }

data class KeyboardRow(
    val keys: List<KeyDef>,
    val heightWeight: Float = 1.0f
)

data class KeyboardLayout(
    val rows: List<KeyboardRow>,
    val isEditingMode: Boolean = false
) {

    val allKeys: List<KeyDef> get() = rows.flatMap { it.keys }

    companion object {
        /**
         * Standard portrait QWERTY phone layout.
         */
        fun qwerty(context: android.content.Context): KeyboardLayout = KeyboardLayout(
            rows = listOf(
                KeyboardRow(listOf(
                    KeyDef(code = KeyEvent.KEYCODE_Q, label = "q"),
                    KeyDef(code = KeyEvent.KEYCODE_W, label = "w"),
                    KeyDef(code = KeyEvent.KEYCODE_E, label = "e"),
                    KeyDef(code = KeyEvent.KEYCODE_R, label = "r"),
                    KeyDef(code = KeyEvent.KEYCODE_T, label = "t"),
                    KeyDef(code = KeyEvent.KEYCODE_Y, label = "y"),
                    KeyDef(code = KeyEvent.KEYCODE_U, label = "u"),
                    KeyDef(code = KeyEvent.KEYCODE_I, label = "i"),
                    KeyDef(code = KeyEvent.KEYCODE_O, label = "o"),
                    KeyDef(code = KeyEvent.KEYCODE_P, label = "p"),
                )),
                KeyboardRow(listOf(
                    KeyDef(code = KeyEvent.KEYCODE_A, label = "a"),
                    KeyDef(code = KeyEvent.KEYCODE_S, label = "s"),
                    KeyDef(code = KeyEvent.KEYCODE_D, label = "d"),
                    KeyDef(code = KeyEvent.KEYCODE_F, label = "f"),
                    KeyDef(code = KeyEvent.KEYCODE_G, label = "g"),
                    KeyDef(code = KeyEvent.KEYCODE_H, label = "h"),
                    KeyDef(code = KeyEvent.KEYCODE_J, label = "j"),
                    KeyDef(code = KeyEvent.KEYCODE_K, label = "k"),
                    KeyDef(code = KeyEvent.KEYCODE_L, label = "l"),
                )),
                KeyboardRow(listOf(
                    KeyDef(code = 0, label = "Shift", isModifier = true, toggleMode = KeyToggleMode.SHIFT, widthWeight = 1.4f),
                    KeyDef(code = KeyEvent.KEYCODE_Z, label = "z"),
                    KeyDef(code = KeyEvent.KEYCODE_X, label = "x"),
                    KeyDef(code = KeyEvent.KEYCODE_C, label = "c"),
                    KeyDef(code = KeyEvent.KEYCODE_V, label = "v"),
                    KeyDef(code = KeyEvent.KEYCODE_B, label = "b"),
                    KeyDef(code = KeyEvent.KEYCODE_N, label = "n"),
                    KeyDef(code = KeyEvent.KEYCODE_M, label = "m"),
                    KeyDef(code = KeyEvent.KEYCODE_DEL, label = "⌫", isFunctional = true, isRepeatable = true, widthWeight = 1.4f),
                )),
                KeyboardRow(listOf(
                    KeyDef(code = 0, label = "Ctrl", isModifier = true, toggleMode = KeyToggleMode.CTRL),
                    KeyDef(code = KeyEvent.KEYCODE_SPACE, label = "space", isFunctional = true, widthWeight = 4.5f),
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_LEFT, label = "←", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_RIGHT, label = "→", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_ENTER, label = "Enter", isFunctional = true, widthWeight = 1.6f),
                )),
            )
        )

        /**
         * Full text-editing layout with arrows, home/end, copy/cut/paste, undo/redo.
         */
        fun editing(): KeyboardLayout = KeyboardLayout(
            rows = listOf(
                KeyboardRow(listOf(
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_LEFT, label = "←", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_UP, label = "↑", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_DOWN, label = "↓", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_DPAD_RIGHT, label = "→", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_HOME, label = "Home", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_MOVE_END, label = "End", isFunctional = true),
                )),
                KeyboardRow(listOf(
                    KeyDef(code = 0, label = "Shift", isModifier = true, toggleMode = KeyToggleMode.SHIFT, widthWeight = 1.4f),
                    KeyDef(code = 0, label = "Ctrl", isModifier = true, toggleMode = KeyToggleMode.CTRL, widthWeight = 1.4f),
                    KeyDef(code = KeyEvent.KEYCODE_COPY, label = "Copy", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_CUT, label = "Cut", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_PASTE, label = "Paste", isFunctional = true),
                )),
                KeyboardRow(listOf(
                    KeyDef(code = 0, label = "All", isFunctional = true, hint = "Ctrl+A"),  // Handled by IME
                    KeyDef(code = 0, label = "Undo", isFunctional = true, hint = "Ctrl+Z"),
                    KeyDef(code = 0, label = "Redo", isFunctional = true, hint = "Ctrl+Y"),
                    KeyDef(code = KeyEvent.KEYCODE_FORWARD_DEL, label = "Del", isFunctional = true),
                    KeyDef(code = KeyEvent.KEYCODE_DEL, label = "⌫", isFunctional = true, isRepeatable = true),
                )),
            ),
            isEditingMode = true
        )
    }
}
