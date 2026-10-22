package com.diptokey.ime.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.diptokey.ime.storage.AppDatabase
import com.diptokey.ime.storage.ClipboardDao
import com.diptokey.ime.storage.ClipboardEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ClipboardEntry(
    val id: Long,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean,
    val isSnippet: Boolean,
    val label: String?
)

/**
 * Clipboard manager. Captures clipboard content **only when the user pastes
 * or copies inside the keyboard's own UI** — it does NOT register a global
 * background listener (Android forbids that since Android 10 for foreground-only
 * access).
 *
 * What we do:
 *  - When the user pastes from this keyboard's clipboard panel, we also
 *    refresh our local history list.
 *  - When the user explicitly taps "Save current clipboard" in settings,
 *    we read the system clipboard (which requires foreground focus).
 *
 * What we DON'T do:
 *  - Continuously poll the system clipboard.
 *  - Capture clipboard contents from other apps in the background.
 *  - Retain clipboard contents in sensitive contexts.
 */
class ClipboardController(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {

    private val dao: ClipboardDao = db.clipboardDao()

    /** Snapshot used for the keyboard panel display. */
    @Volatile
    private var cached: List<ClipboardEntry> = emptyList()
    private val cacheLock = Any()

    init {
        // Initial load
        scope.launch(Dispatchers.IO) { refreshCache() }
    }

    fun getEntriesForDisplay(): List<ClipboardEntry> = cached

    suspend fun refreshCache() {
        val entries = dao.getAll().map { it.toUi() }
        synchronized(cacheLock) { cached = entries }
    }

    fun captureFromSystemClipboard() {
        scope.launch(Dispatchers.IO) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = cm?.primaryClip ?: return@launch
            if (clip.itemCount == 0) return@launch
            val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return@launch
            if (text.isBlank()) return@launch
            // Don't store huge entries.
            if (text.length > MAX_ENTRY_LENGTH) return@launch
            dao.insert(ClipboardEntryEntity(text = text, createdAt = System.currentTimeMillis()))
            refreshCache()
        }
    }

    fun addSnippet(text: String, label: String? = null) {
        scope.launch(Dispatchers.IO) {
            dao.insert(
                ClipboardEntryEntity(
                    text = text,
                    createdAt = System.currentTimeMillis(),
                    isSnippet = true,
                    label = label
                )
            )
            refreshCache()
        }
    }

    fun paste(id: Long, onText: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val entry = dao.getAll().firstOrNull { it.id == id } ?: return@launch
            withContext(Dispatchers.Main) { onText(entry.text) }
        }
    }

    fun pin(id: Long, pinned: Boolean) {
        scope.launch(Dispatchers.IO) {
            dao.setPinned(id, pinned)
            refreshCache()
        }
    }

    fun delete(id: Long) {
        scope.launch(Dispatchers.IO) {
            dao.delete(id)
            refreshCache()
        }
    }

    fun clearUnpinned() {
        scope.launch(Dispatchers.IO) {
            dao.clearUnpinned()
            refreshCache()
        }
    }

    fun clearAll() {
        scope.launch(Dispatchers.IO) {
            dao.clearAll()
            refreshCache()
        }
    }

    fun search(q: String, callback: (List<ClipboardEntry>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val res = if (q.isBlank()) dao.getAll() else dao.search("%$q%")
            val ui = res.map { it.toUi() }
            withContext(Dispatchers.Main) { callback(ui) }
        }
    }

    private fun ClipboardEntryEntity.toUi() = ClipboardEntry(id, text, createdAt, pinned, isSnippet, label)

    companion object {
        const val MAX_ENTRY_LENGTH = 4096
    }
}
