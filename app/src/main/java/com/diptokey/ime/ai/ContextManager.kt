package com.diptokey.ime.ai

/**
 * Manages the AI request context: instruction, selected text, pasted text,
 * previous responses, reusable snippets. Users can inspect & edit everything
 * before a request goes out. No text is ever collected silently from apps.
 */
class ContextManager {

    @Volatile
    var items: List<ContextItem> = emptyList()
        private set

    fun add(item: ContextItem) {
        items = items + item
    }

    fun remove(id: String) {
        items = items.filter { it.id != id }
    }

    fun clearAll() { items = emptyList() }

    fun buildPrompt(instruction: String, sourceText: String?, action: AIAction): String {
        val sb = StringBuilder()
        sb.append(actionPrompt(action))
        sb.append("\n\nInstruction: ").append(instruction)
        if (!sourceText.isNullOrBlank()) {
            sb.append("\n\nSource text:\n").append(sourceText)
        }
        if (items.isNotEmpty()) {
            sb.append("\n\nAdditional context:")
            items.forEach { sb.append("\n- [").append(it.label).append("] ").append(it.text) }
        }
        sb.append("\n\nRespond directly and concisely.")
        return sb.toString()
    }

    private fun actionPrompt(action: AIAction): String = when (action) {
        AIAction.GENERATE -> "Generate a helpful response."
        AIAction.REWRITE -> "Rewrite the source text, preserving meaning."
        AIAction.GRAMMAR -> "Improve the grammar of the source text."
        AIAction.TRANSLATE -> "Translate the source text. Default target: Bangla unless otherwise specified."
        AIAction.SUMMARIZE -> "Summarize the source text in fewer sentences."
        AIAction.CONTINUE -> "Continue writing from where the source text ends."
        AIAction.TONE -> "Change the tone of the source text. Default: professional."
        AIAction.SHORTER -> "Make the source text shorter while preserving meaning."
        AIAction.DETAILED -> "Expand the source text with more detail."
        AIAction.EXPLAIN -> "Explain the source text in simple terms."
        AIAction.ANSWER -> "Answer the user's question. Use source text if provided."
    }
}

data class ContextItem(
    val id: String,
    val label: String,
    val text: String,
)
