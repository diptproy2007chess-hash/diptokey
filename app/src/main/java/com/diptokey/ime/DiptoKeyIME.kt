package com.diptokey.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.diptokey.ime.ai.AIOrchestrator
import com.diptokey.ime.ai.ContextManager
import com.diptokey.ime.clipboard.ClipboardController
import com.diptokey.ime.editing.EditingController
import com.diptokey.ime.keyboard.KeyboardLayout
import com.diptokey.ime.keyboard.KeyboardView
import com.diptokey.ime.keyboard.ModifierState
import com.diptokey.ime.security.EncryptedSecretStore
import com.diptokey.ime.storage.AppDatabase
import com.diptokey.ime.suggestions.SuggestionEngine
import com.diptokey.ime.transliteration.TransliterationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The main InputMethodService for DiptoKey.
 *
 * Lifecycle responsibilities:
 *  - Create and recycle the [KeyboardView] on onCreateInputView / onDestroy.
 *  - Adapt to [EditorInfo] (password, numeric, email, etc.).
 *  - Bridge UI events from [KeyboardView] to [EditingController] and the
 *    InputConnection.
 *  - Coordinate suggestion strip via [SuggestionEngine] and [TransliterationEngine].
 *  - Adapt keyboard height / theme from user preferences.
 */
class DiptoKeyIME : InputMethodService() {

    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var keyboardView: KeyboardView
    private lateinit var modifierState: ModifierState
    private lateinit var editingController: EditingController
    private lateinit var transliteration: TransliterationEngine
    private lateinit var suggestions: SuggestionEngine
    private lateinit var clipboard: ClipboardController
    private lateinit var contextManager: ContextManager
    private lateinit var aiOrchestrator: AIOrchestrator
    private lateinit var secretStore: EncryptedSecretStore

    /** Whether typing in the current field is sensitive (password, credit card, etc.). */
    private var isSensitiveField: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val app = application as? DiptoKeyApp ?: DiptoKeyApp.instance
        secretStore = app.secretStore
        modifierState = ModifierState()
        editingController = EditingController(this)
        editingController.modifierState = modifierState
        transliteration = TransliterationEngine.get()
        suggestions = SuggestionEngine.get(applicationContext)
        clipboard = ClipboardController(this, app.database, imeScope)
        contextManager = ContextManager()
        aiOrchestrator = AIOrchestrator.get(applicationContext, secretStore, contextManager)
    }

    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        keyboardView = KeyboardView(this).also { kv ->
            kv.onChar = { c -> onCharacterTyped(c) }
            kv.onKeyCode = { code, meta -> onKeyCode(code, meta) }
            kv.onSuggestionPicked = { text -> onSuggestionAccepted(text) }
            kv.onSpaceSwipe = { dxPx -> onCursorSwipe(dxPx) }
            kv.onToolbarAction = { action -> onToolbarAction(action) }
            kv.setComposingTextListener { composing -> onComposingUpdated(composing) }
            kv.setModifierState(modifierState)
        }
        // Apply current EditorInfo to it.
        onConfigureViews(currentInputEditorInfo)
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (attribute != null) {
            isSensitiveField = isSensitiveInputType(attribute)
            suggestions.onInputStart(isSensitiveField)
            if (::keyboardView.isInitialized) {
                if (isSensitiveField) {
                    modifierState.reset()
                    keyboardView.setSecureMode(true)
                } else {
                    keyboardView.setSecureMode(false)
                }
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!isSensitiveField) {
            // Could refresh suggestions here based on selection changes.
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        suggestions.onInputEnd()
    }

    override fun onDestroy() {
        imeScope.cancel()
        super.onDestroy()
    }

    // ----- Input handling -----

    private fun onCharacterTyped(c: Char) {
        if (isSensitiveField) {
            // For secure fields, never use composing mode — type directly.
            currentInputConnection?.commitText(c.toString(), 1)
            return
        }
        currentInputConnection?.let { ic ->
            // Use composing mode to enable transliteration & suggestions.
            val before = ic.getTextBeforeCursor(COMPOSING_LOOKBACK, 0) ?: ""
            val composing = findComposingRegion(before.toString())
            val newComposing = composing + c
            ic.setComposingText(newComposing, 1)
            updateSuggestions(newComposing)
        }
        modifierState.onKeyConsumed()
        keyboardView.refreshModifierVisuals()
    }

    private fun onKeyCode(code: Int, meta: Int) {
        val ic = currentInputConnection
        when (code) {
            KeyEvent.KEYCODE_DEL -> {
                ic?.deleteSurroundingText(1, 0)
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (isSensitiveField) {
                    ic?.commitText(" ", 1)
                } else {
                    ic?.commitText(" ", 1)
                    suggestions.onWordCommitted(extractLastWord())
                }
            }
            KeyEvent.KEYCODE_ENTER -> {
                val ei = currentInputEditorInfo
                val action = ei?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
                when (action) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_SEARCH -> {
                        ic?.performEditorAction(action)
                    }
                    else -> {
                        ic?.commitText("\n", 1)
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> editingController.moveCursorLeft(ic, meta)
            KeyEvent.KEYCODE_DPAD_RIGHT -> editingController.moveCursorRight(ic, meta)
            KeyEvent.KEYCODE_DPAD_UP -> editingController.moveCursorUp(ic, meta)
            KeyEvent.KEYCODE_DPAD_DOWN -> editingController.moveCursorDown(ic, meta)
            KeyEvent.KEYCODE_HOME -> editingController.moveLineStart(ic)
            KeyEvent.KEYCODE_MOVE_END -> editingController.moveLineEnd(ic)
            KeyEvent.KEYCODE_COPY -> editingController.copy(ic)
            KeyEvent.KEYCODE_CUT -> editingController.cut(ic)
            KeyEvent.KEYCODE_PASTE -> editingController.paste(ic)
            KeyEvent.KEYCODE_A -> {
                if (modifierState.isCtrlActive) editingController.selectAll(ic) else onCharacterTyped('a')
            }
            KeyEvent.KEYCODE_C -> {
                if (modifierState.isCtrlActive) editingController.copy(ic) else onCharacterTyped('c')
            }
            KeyEvent.KEYCODE_V -> {
                if (modifierState.isCtrlActive) editingController.paste(ic) else onCharacterTyped('v')
            }
            KeyEvent.KEYCODE_X -> {
                if (modifierState.isCtrlActive) editingController.cut(ic) else onCharacterTyped('x')
            }
            KeyEvent.KEYCODE_Z -> {
                if (modifierState.isCtrlActive) editingController.undo(ic) else onCharacterTyped('z')
            }
            KeyEvent.KEYCODE_Y -> {
                if (modifierState.isCtrlActive) editingController.redo(ic) else onCharacterTyped('y')
            }
            else -> {
                if (code in 32..255) onCharacterTyped(code.toChar())
            }
        }
        modifierState.onKeyConsumed()
        keyboardView.refreshModifierVisuals()
    }

    private fun onSuggestionAccepted(text: String) {
        val ic = currentInputConnection ?: return
        if (isSensitiveField) {
            ic.commitText(text, 1)
            return
        }
        // Replace the current composing region with the accepted text.
        ic.finishComposingText()
        // Remove the just-finished word (we already committed via setComposingText), so delete back.
        val wordLen = currentComposingWord.length
        if (wordLen > 0) ic.deleteSurroundingText(wordLen, 0)
        ic.commitText(text, 1)
        currentComposingWord = ""
        suggestions.onWordCommitted(text)
    }

    private fun onComposingUpdated(composing: String) {
        currentComposingWord = composing
        if (!isSensitiveField) updateSuggestions(composing)
    }

    private fun onCursorSwipe(dxPx: Float) {
        val ic = currentInputConnection ?: return
        // Map swipe distance to characters. Negative → left, positive → right.
        val charStep = (dxPx / SWIPE_CHARS_PER_PX).toInt().coerceIn(-40, 40)
        if (charStep != 0) {
            editingController.moveCursorRelative(ic, charStep)
        }
    }

    private fun onToolbarAction(action: KeyboardView.ToolbarAction) {
        when (action) {
            KeyboardView.ToolbarAction.AI -> keyboardView.openAIPanel()
            KeyboardView.ToolbarAction.CLIPBOARD -> keyboardView.openClipboardPanel(clipboard.getEntriesForDisplay())
            KeyboardView.ToolbarAction.MORE -> keyboardView.toggleEditingMode()
            KeyboardView.ToolbarAction.SETTINGS -> keyboardView.openSettings()
        }
    }

    // ----- Helpers -----

    private var currentComposingWord: String = ""

    private fun updateSuggestions(composing: String) {
        if (isSensitiveField || composing.isBlank()) {
            keyboardView.setSuggestions(emptyList())
            return
        }
        val candidates = mutableListOf<String>()
        // 1. Bangla transliteration candidates (highest priority if latin text).
        if (composing.any { it.isLetter() && it in 'a'..'z' || it in 'A'..'Z' }) {
            candidates += transliteration.transliterate(composing).take(3)
        }
        // 2. English completions.
        candidates += suggestions.completeEnglish(composing).take(3)
        // 3. Next-word suggestions (if composing blank).
        if (composing.isBlank()) {
            candidates += suggestions.nextWordPredictions().take(3)
        }
        keyboardView.setSuggestions(candidates.distinct().take(8))
    }

    private fun findComposingRegion(textBeforeCursor: String): String {
        if (textBeforeCursor.isEmpty()) return ""
        // Take characters back until we hit a whitespace boundary.
        val sb = StringBuilder()
        for (i in textBeforeCursor.length - 1 downTo 0) {
            val ch = textBeforeCursor[i]
            if (ch.isWhitespace()) break
            sb.insert(0, ch)
        }
        return sb.toString()
    }

    private fun extractLastWord(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        return findComposingRegion(before)
    }

    private fun isSensitiveInputType(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> {
                when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return true
                }
            }
            InputType.TYPE_CLASS_NUMBER -> {
                when (variation) {
                    InputType.TYPE_NUMBER_VARIATION_PASSWORD -> return true
                }
            }
        }
        // Also respect the EditorInfo flags.
        return (info.inputType and InputType.TYPE_MASK_FLAGS) and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
    }

    private fun onConfigureViews(info: EditorInfo?) {
        if (!::keyboardView.isInitialized) return
        keyboardView.apply {
            setLayout(KeyboardLayout.qwerty(this@DiptoKeyIME))
            setImeOptions(info?.imeOptions ?: 0)
        }
    }

    companion object {
        private const val COMPOSING_LOOKBACK = 64
        private const val SWIPE_CHARS_PER_PX = 18f
    }
}
