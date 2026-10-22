package com.diptokey.ime.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.diptokey.ime.R
import com.diptokey.ime.clipboard.ClipboardEntry
import com.diptokey.ime.settings.SettingsActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A fully custom keyboard View that draws keys, toolbar, suggestion strip, AI panel,
 * and clipboard panel. Drawing manually (instead of using the legacy Android
 * KeyboardView) gives us tight control over layout and is the recommended approach
 * for modern Android IMEs.
 *
 * Touch handling:
 *  - Multi-touch down on multiple keys is NOT supported (only the first pointer is
 *    tracked) — keeps code simple and matches Samsung Keyboard behavior.
 *  - Long-press on backspace triggers repeat-delete.
 *  - Horizontal swipe on the space key performs cursor movement.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class PanelMode { KEYBOARD, AI, CLIPBOARD, EDITING }

    sealed class ToolbarAction {
        object AI : ToolbarAction()
        object CLIPBOARD : ToolbarAction()
        object MORE : ToolbarAction()
        object SETTINGS : ToolbarAction()
    }

    // ---- Callbacks wired by DiptoKeyIME ----
    var onChar: ((Char) -> Unit)? = null
    var onKeyCode: ((Int, Int) -> Unit)? = null
    var onSuggestionPicked: ((String) -> Unit)? = null
    var onSpaceSwipe: ((Float) -> Unit)? = null
    var onToolbarAction: ((ToolbarAction) -> Unit)? = null
    var onComposingUpdate: ((String) -> Unit)? = null

    private var panelMode: PanelMode = PanelMode.KEYBOARD
    private var editingLayout: KeyboardLayout = KeyboardLayout.editing()
    private var currentLayout: KeyboardLayout = KeyboardLayout.qwerty(context)
    private var modifierState: ModifierState = ModifierState()
    private var suggestionList: List<String> = emptyList()
    private var secureMode: Boolean = false

    // AI panel state
    private var aiInputText: String = ""
    private var aiResultText: String = ""
    private var aiLoading: Boolean = false
    private var aiAction: String = "Generate"

    // Clipboard panel state
    private var clipboardEntries: List<ClipboardEntry> = emptyList()
    private var clipboardSearch: String = ""

    // ---- Drawing state ----
    private val density = resources.displayMetrics.density
    private val keyHeightPx = resources.getDimension(R.dimen.key_height)
    private val keyHPad = resources.getDimension(R.dimen.key_padding_horizontal)
    private val keyHGap = resources.getDimension(R.dimen.key_horizontal_gap)
    private val keyVGap = resources.getDimension(R.dimen.key_vertical_gap)
    private val suggestionHeight = resources.getDimension(R.dimen.suggestion_height)
    private val toolbarHeight = resources.getDimension(R.dimen.toolbar_height)
    private val cornerRadius = resources.getDimension(R.dimen.key_corner_radius)

    private val isDark: Boolean by lazy {
        val flags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        flags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private val bgKey: Int by lazy { if (isDark) Color.parseColor("#FF2C2C30") else Color.WHITE }
    private val bgKeyPressed: Int by lazy { if (isDark) Color.parseColor("#FF3A3A40") else Color.parseColor("#FFD9DEE6") }
    private val bgModifierActive: Int by lazy { Color.parseColor("#FF0A84FF") }
    private val bgFunctional: Int by lazy { if (isDark) Color.parseColor("#FF1F1F23") else Color.parseColor("#FFE7E9EE") }
    private val bgSurface: Int by lazy { if (isDark) Color.parseColor("#FF1B1B1F") else Color.parseColor("#FFEFF1F4") }
    private val bgToolbar: Int by lazy { if (isDark) Color.parseColor("#FF141416") else Color.parseColor("#FFE7E9EE") }
    private val bgSuggestion: Int by lazy { if (isDark) Color.parseColor("#FF222226") else Color.parseColor("#FFF6F7F9") }
    private val textPrimary: Int by lazy { if (isDark) Color.parseColor("#FFE7E7EA") else Color.parseColor("#FF1B1B1F") }
    private val textSecondary: Int by lazy { if (isDark) Color.parseColor("#FFA9A9B0") else Color.parseColor("#FF6A6A6A") }
    private val accentColor: Int by lazy { Color.parseColor("#FF0A84FF") }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary
        textSize = resources.getDimension(R.dimen.key_text_size)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary
        textSize = resources.getDimension(R.dimen.key_label_size)
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
    }
    private val suggestionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.suggestion_text_size)
        textAlign = Paint.Align.CENTER
    }
    private val toolbarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }
    private val rectBuffer = RectF()
    private val textBounds = Rect()

    // ---- Touch state ----
    private var pressedKeyIndex: Int = -1
    private var pressedPointerId: Int = -1
    private var spaceSwipeStartX: Float = Float.NaN
    private var lastSwipeDelta: Float = 0f
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatingKeyCode: Int = 0
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (repeatingKeyCode != 0) {
                onKeyCode?.invoke(repeatingKeyCode, 0)
                repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }

    private var composingText: String = ""

    // ---- Public API ----

    fun setLayout(layout: KeyboardLayout) {
        currentLayout = layout
        panelMode = PanelMode.KEYBOARD
        invalidate()
    }

    fun setModifierState(state: ModifierState) {
        modifierState = state
        refreshModifierVisuals()
    }

    fun setSuggestions(list: List<String>) {
        suggestionList = list
        invalidate()
    }

    fun setSecureMode(secure: Boolean) {
        secureMode = secure
        if (secure) {
            composingText = ""
            suggestionList = emptyList()
        }
        invalidate()
    }

    fun refreshModifierVisuals() { invalidate() }

    fun setImeOptions(imeOptions: Int) {
        // Could customise Enter label based on action. We keep "Enter" for simplicity
        // and rely on IME to perform the proper action on key press.
        invalidate()
    }

    fun setComposingTextListener(listener: (String) -> Unit) {
        onComposingUpdate = listener
    }

    fun openAIPanel() { panelMode = PanelMode.AI; invalidate() }
    fun openClipboardPanel(entries: List<ClipboardEntry>) {
        clipboardEntries = entries
        clipboardSearch = ""
        panelMode = PanelMode.CLIPBOARD
        invalidate()
    }
    fun toggleEditingMode() {
        panelMode = if (panelMode == PanelMode.EDITING) PanelMode.KEYBOARD else PanelMode.EDITING
        invalidate()
    }
    fun openSettings() {
        val i = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(i)
    }

    fun setAILoading(loading: Boolean) { aiLoading = loading; invalidate() }
    fun setAIResult(result: String) { aiResultText = result; aiLoading = false; invalidate() }
    fun setAIAction(action: String) { aiAction = action; invalidate() }
    fun setAIInput(text: String) { aiInputText = text; invalidate() }

    // ---- Layout / measure ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Compute total height based on rows + suggestion + toolbar.
        val rows = layoutToDraw().rows
        val totalKeyHeight = (keyHeightPx * rows.size).roundToInt()
        val total = (suggestionHeight + toolbarHeight + totalKeyHeight +
                resources.getDimension(R.dimen.keyboard_top_padding) +
                resources.getDimension(R.dimen.keyboard_bottom_padding)).roundToInt()
        setMeasuredDimension(width, total.coerceAtLeast(180))
    }

    private fun layoutToDraw(): KeyboardLayout = when (panelMode) {
        PanelMode.EDITING -> editingLayout
        else -> currentLayout
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgSurface)
        when (panelMode) {
            PanelMode.KEYBOARD, PanelMode.EDITING -> {
                drawToolbar(canvas)
                drawSuggestions(canvas)
                drawKeyboard(canvas)
            }
            PanelMode.AI -> drawAIPanel(canvas)
            PanelMode.CLIPBOARD -> drawClipboardPanel(canvas)
        }
    }

    private fun drawToolbar(canvas: Canvas) {
        val top = 0
        rectBuffer.set(0f, top.toFloat(), width.toFloat(), (top + toolbarHeight))
        canvas.drawRect(rectBuffer, Paint().apply { color = bgToolbar })

        val icons = listOf(
            "AI" to ToolbarAction.AI,
            "📋" to ToolbarAction.CLIPBOARD,
            "✏" to ToolbarAction.MORE,
            "⚙" to ToolbarAction.SETTINGS,
        )
        val slotWidth = width / icons.size.toFloat()
        icons.forEachIndexed { i, (label, action) ->
            toolbarTextPaint.color = if (action == ToolbarAction.MORE && panelMode == PanelMode.EDITING) accentColor else textPrimary
            canvas.drawText(label, (i + 0.5f) * slotWidth, top + toolbarHeight * 0.62f, toolbarTextPaint)
        }
    }

    private fun drawSuggestions(canvas: Canvas) {
        val top = toolbarHeight
        rectBuffer.set(0f, top, width.toFloat(), top + suggestionHeight)
        canvas.drawRect(rectBuffer, Paint().apply { color = bgSuggestion })

        if (secureMode) {
            suggestionTextPaint.color = textSecondary
            canvas.drawText("Suggestions disabled (secure field)", width / 2f, top + suggestionHeight * 0.62f, suggestionTextPaint)
            return
        }
        if (suggestionList.isEmpty()) {
            suggestionTextPaint.color = textSecondary
            canvas.drawText(composingText.ifBlank { " " }, width / 2f, top + suggestionHeight * 0.62f, suggestionTextPaint)
            return
        }
        val slotWidth = width / suggestionList.size.toFloat()
        suggestionList.forEachIndexed { i, text ->
            suggestionTextPaint.color = textPrimary
            canvas.drawText(text, (i + 0.5f) * slotWidth, top + suggestionHeight * 0.62f, suggestionTextPaint)
        }
    }

    private fun drawKeyboard(canvas: Canvas) {
        val layout = layoutToDraw()
        val topPad = resources.getDimension(R.dimen.keyboard_top_padding)
        val hPad = resources.getDimension(R.dimen.keyboard_horizontal_padding)

        var y = toolbarHeight + suggestionHeight + topPad
        layout.rows.forEach { row ->
            val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
            var x = hPad
            val availableWidth = width - 2 * hPad
            row.keys.forEach { key ->
                val w = (availableWidth * (key.widthWeight / totalWeight)) - keyHGap
                drawKey(canvas, key, x, y, w, keyHeightPx)
                x += w + keyHGap
            }
            y += keyHeightPx + keyVGap
        }
    }

    private fun drawKey(canvas: Canvas, key: KeyDef, x: Float, y: Float, w: Float, h: Float) {
        rectBuffer.set(x, y, x + w, y + h)
        val baseColor = when {
            key.toggleMode == KeyToggleMode.SHIFT && modifierState.isShiftActive -> bgModifierActive
            key.toggleMode == KeyToggleMode.CTRL && modifierState.isCtrlActive -> bgModifierActive
            key.isFunctional || key.isModifier -> bgFunctional
            else -> bgKey
        }
        keyPaint.color = baseColor
        canvas.drawRoundRect(rectBuffer, cornerRadius, cornerRadius, keyPaint)

        // Pressed highlight
        val idx = currentLayout.allKeys.indexOf(key)
        if (idx == pressedKeyIndex) {
            keyPaint.color = bgKeyPressed
            canvas.drawRoundRect(rectBuffer, cornerRadius, cornerRadius, keyPaint)
        }

        // Label
        val label = displayLabel(key)
        val paintForLabel = if (key.label.length <= 2 && !key.isModifier) textPaint else labelPaint
        paintForLabel.color = if ((key.toggleMode == KeyToggleMode.SHIFT && modifierState.isShiftActive) ||
            (key.toggleMode == KeyToggleMode.CTRL && modifierState.isCtrlActive)) Color.WHITE else textPrimary
        paintForLabel.getTextBounds(label, 0, label.length, textBounds)
        val baseline = y + h / 2 + textBounds.height() / 2
        canvas.drawText(label, x + w / 2, baseline, paintForLabel)
        if (key.hint.isNotBlank()) {
            canvas.drawText(key.hint, x + w / 2, y + h - 6 * density, hintPaint)
        }
    }

    private fun displayLabel(key: KeyDef): String {
        return when {
            key.label == "space" -> ""
            else -> key.label
        }
    }

    // ---- AI panel drawing ----

    private fun drawAIPanel(canvas: Canvas) {
        // Title bar
        rectBuffer.set(0f, 0f, width.toFloat(), toolbarHeight)
        canvas.drawRect(rectBuffer, Paint().apply { color = bgToolbar })
        toolbarTextPaint.color = textPrimary
        canvas.drawText("AI — $aiAction", width / 2f, toolbarHeight * 0.62f, toolbarTextPaint)

        // Input area
        var y = toolbarHeight + 8f * density
        suggestionTextPaint.color = textSecondary
        canvas.drawText("Instruction:", 16f * density, y + 14f * density, suggestionTextPaint)
        y += 20f * density
        suggestionTextPaint.color = textPrimary
        suggestionTextPaint.textSize = 14f * density
        // Wrap aiInputText
        val wrapped = wrapText(aiInputText, width - 32f * density, suggestionTextPaint)
        wrapped.forEach { line ->
            canvas.drawText(line, 16f * density, y + 14f * density, suggestionTextPaint)
            y += 18f * density
        }

        // Result
        y += 10f * density
        suggestionTextPaint.color = accentColor
        canvas.drawText("Result:", 16f * density, y + 14f * density, suggestionTextPaint)
        y += 20f * density
        suggestionTextPaint.color = textPrimary
        val resultText = if (aiLoading) "Generating…" else aiResultText.ifBlank { "(no result yet)" }
        val resultWrapped = wrapText(resultText, width - 32f * density, suggestionTextPaint)
        resultWrapped.take(8).forEach { line ->
            canvas.drawText(line, 16f * density, y + 14f * density, suggestionTextPaint)
            y += 18f * density
        }

        // Bottom action buttons
        val actions = listOf("Insert", "Replace", "Copy", "Regenerate", "Close")
        val slotWidth = width / actions.size.toFloat()
        canvas.drawRect(0f, height - 44f * density, width.toFloat(), height.toFloat(),
            Paint().apply { color = bgToolbar })
        actions.forEachIndexed { i, label ->
            toolbarTextPaint.color = textPrimary
            canvas.drawText(label, (i + 0.5f) * slotWidth, height - 18f * density, toolbarTextPaint)
        }
    }

    private fun drawClipboardPanel(canvas: Canvas) {
        rectBuffer.set(0f, 0f, width.toFloat(), toolbarHeight)
        canvas.drawRect(rectBuffer, Paint().apply { color = bgToolbar })
        toolbarTextPaint.color = textPrimary
        canvas.drawText("Clipboard — " + clipboardEntries.size + " items", width / 2f, toolbarHeight * 0.62f, toolbarTextPaint)

        val filtered = if (clipboardSearch.isBlank()) clipboardEntries
            else clipboardEntries.filter { it.text.contains(clipboardSearch, ignoreCase = true) }

        var y = toolbarHeight + 16f * density
        suggestionTextPaint.textSize = 13f * density
        suggestionTextPaint.color = textPrimary
        if (filtered.isEmpty()) {
            suggestionTextPaint.color = textSecondary
            canvas.drawText("No clipboard entries", width / 2f, y + 14f * density, suggestionTextPaint)
        } else {
            filtered.take(8).forEach { entry ->
                val preview = entry.text.take(60).replace("\n", " ")
                canvas.drawText(preview, 16f * density, y + 14f * density, suggestionTextPaint)
                y += 28f * density
            }
        }

        // Bottom actions
        val actions = listOf("Search", "Paste", "Pin", "Delete", "Close")
        val slotWidth = width / actions.size.toFloat()
        canvas.drawRect(0f, height - 44f * density, width.toFloat(), height.toFloat(),
            Paint().apply { color = bgToolbar })
        actions.forEachIndexed { i, label ->
            toolbarTextPaint.color = textPrimary
            canvas.drawText(label, (i + 0.5f) * slotWidth, height - 18f * density, toolbarTextPaint)
        }
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(test) > maxWidth) {
                if (current.isNotBlank()) lines += current
                current = word
            } else {
                current = test
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    // ---- Touch handling ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedPointerId = event.getPointerId(0)
                handleDown(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleUp(x, y)
                pressedKeyIndex = -1
                pressedPointerId = -1
                invalidate()
            }
        }
        return true
    }

    private fun handleDown(x: Float, y: Float) {
        when (panelMode) {
            PanelMode.AI -> handleAIPanelTap(x, y)
            PanelMode.CLIPBOARD -> handleClipboardPanelTap(x, y)
            else -> handleKeyboardTap(x, y)
        }
    }

    private fun handleKeyboardTap(x: Float, y: Float) {
        // Toolbar row?
        if (y < toolbarHeight) {
            val slotWidth = width / 4f
            when ((x / slotWidth).toInt()) {
                0 -> onToolbarAction?.invoke(ToolbarAction.AI)
                1 -> onToolbarAction?.invoke(ToolbarAction.CLIPBOARD)
                2 -> onToolbarAction?.invoke(ToolbarAction.MORE)
                3 -> onToolbarAction?.invoke(ToolbarAction.SETTINGS)
            }
            return
        }
        // Suggestion strip?
        if (y < toolbarHeight + suggestionHeight && !secureMode && suggestionList.isNotEmpty()) {
            val slotWidth = width / suggestionList.size.toFloat()
            val idx = ((x - 0) / slotWidth).toInt().coerceIn(0, suggestionList.size - 1)
            onSuggestionPicked?.invoke(suggestionList[idx])
            return
        }
        // Keys?
        val layout = layoutToDraw()
        val topPad = resources.getDimension(R.dimen.keyboard_top_padding)
        val hPad = resources.getDimension(R.dimen.keyboard_horizontal_padding)
        var ry = toolbarHeight + suggestionHeight + topPad
        layout.rows.forEachIndexed { rowIdx, row ->
            if (y in ry..(ry + keyHeightPx)) {
                val totalWeight = row.keys.sumOf { it.widthWeight.toDouble() }.toFloat()
                var rx = hPad
                val availableWidth = width - 2 * hPad
                row.keys.forEachIndexed { keyIdx, key ->
                    val w = (availableWidth * (key.widthWeight / totalWeight)) - keyHGap
                    if (x in rx..(rx + w)) {
                        // Found key
                        val flatIdx = layout.allKeys.indexOf(key)
                        pressedKeyIndex = flatIdx
                        performHaptic()
                        onKeyPressed(key)
                        invalidate()
                        return
                    }
                    rx += w + keyHGap
                }
            }
            ry += keyHeightPx + keyVGap
        }
    }

    private fun onKeyPressed(key: KeyDef) {
        // Modifier toggles
        when (key.toggleMode) {
            KeyToggleMode.SHIFT -> { modifierState.toggleShift(); refreshModifierVisuals(); return }
            KeyToggleMode.CTRL -> { modifierState.toggleCtrl(); refreshModifierVisuals(); return }
            else -> {}
        }
        // Special label-based functional keys (editing mode "Undo"/"Redo"/"All")
        when (key.label) {
            "Undo" -> { onKeyCode?.invoke(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.META_CTRL_ON); return }
            "Redo" -> { onKeyCode?.invoke(android.view.KeyEvent.KEYCODE_Y, android.view.KeyEvent.META_CTRL_ON); return }
            "All" -> { onKeyCode?.invoke(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.META_CTRL_ON); return }
        }
        // Backspace with repeat
        if (key.code == android.view.KeyEvent.KEYCODE_DEL) {
            onKeyCode?.invoke(key.code, 0)
            if (key.isRepeatable) {
                repeatingKeyCode = key.code
                repeatHandler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
            }
            return
        }
        // Character keys
        if (key.code == 0 && key.label.length == 1) {
            onChar?.invoke(key.label[0])
        } else if (key.code in 32..255 && key.label.length == 1) {
            // Apply shift modifier
            val ch = if (modifierState.isShiftActive) key.label.uppercase().firstOrNull() ?: key.label.first()
                     else key.label.first()
            onChar?.invoke(ch)
        } else if (key.code != 0) {
            onKeyCode?.invoke(key.code, 0)
        }
    }

    private fun handleMove(x: Float, y: Float) {
        // If swiping over the space key area, perform cursor gesture.
        if (panelMode != PanelMode.KEYBOARD) return
        val topPad = resources.getDimension(R.dimen.keyboard_top_padding)
        val rows = layoutToDraw().rows
        // Find which row the touch is on
        var ry = toolbarHeight + suggestionHeight + topPad
        for ((idx, row) in rows.withIndex()) {
            val rowBottom = ry + keyHeightPx
            if (y in ry..rowBottom) {
                // Check if this is the space row (last row, contains space)
                val spaceKey = row.keys.firstOrNull { it.label == "space" }
                if (spaceKey != null) {
                    if (spaceSwipeStartX.isNaN()) {
                        spaceSwipeStartX = x
                        lastSwipeDelta = 0f
                    } else {
                        val delta = x - spaceSwipeStartX
                        val step = ((delta - lastSwipeDelta) / 18f)
                        if (abs(step) >= 1f) {
                            lastSwipeDelta = delta
                            onSpaceSwipe?.invoke((delta / 18f))
                        }
                    }
                }
                return
            }
            ry = rowBottom + resources.getDimension(R.dimen.key_vertical_gap)
        }
    }

    private fun handleUp(x: Float, y: Float) {
        repeatHandler.removeCallbacks(repeatRunnable)
        repeatingKeyCode = 0
        spaceSwipeStartX = Float.NaN
        lastSwipeDelta = 0f
    }

    private fun handleAIPanelTap(x: Float, y: Float) {
        val actions = listOf("Insert", "Replace", "Copy", "Regenerate", "Close")
        if (y > height - 44f * density) {
            val slotWidth = width / actions.size.toFloat()
            val idx = (x / slotWidth).toInt().coerceIn(0, actions.size - 1)
            when (actions[idx]) {
                "Close" -> { panelMode = PanelMode.KEYBOARD; invalidate() }
                // Others handled by IME via dedicated callbacks (not wired in MVP)
            }
        }
    }

    private fun handleClipboardPanelTap(x: Float, y: Float) {
        val actions = listOf("Search", "Paste", "Pin", "Delete", "Close")
        if (y > height - 44f * density) {
            val slotWidth = width / actions.size.toFloat()
            val idx = (x / slotWidth).toInt().coerceIn(0, actions.size - 1)
            when (actions[idx]) {
                "Close" -> { panelMode = PanelMode.KEYBOARD; invalidate() }
            }
        }
    }

    private fun performHaptic() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    companion object {
        private const val REPEAT_DELAY_MS = 400L
        private const val REPEAT_INTERVAL_MS = 80L
    }
}
