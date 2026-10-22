package com.diptokey.ime.fonts

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Imports and manages custom keyboard fonts (.ttf, .otf).
 *
 * Fonts live in app-private storage under `files/fonts/`.
 * We validate file integrity by SHA-256 and refuse files > 8 MB to prevent
 * accidental memory pressure.
 *
 * IMPORTANT: These fonts affect ONLY the keyboard UI rendering. They do NOT
 * change how other apps render typed text — Android does not allow IMEs to
 * influence the receiving editor's font.
 */
class FontManager(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "fonts").apply { if (!exists()) mkdirs() }
    }

    fun listInstalledFonts(): List<InstalledFont> {
        return dir.listFiles { f -> f.isFile && (f.extension.equals("ttf", true) || f.extension.equals("otf", true)) }
            ?.sortedBy { it.nameWithoutExtension }
            ?.map { InstalledFont(name = it.nameWithoutExtension, path = it.absolutePath, sizeBytes = it.length()) }
            ?: emptyList()
    }

    /** Import a font from an input stream. Returns the saved file or null on failure. */
    fun importFont(name: String, input: InputStream): Result<InstalledFont> {
        return try {
            val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim().ifBlank { "font_${System.currentTimeMillis()}" }
            val outFile = File(dir, "$safeName.ttf")
            val bytes = input.readBytes()
            if (bytes.size > MAX_FONT_BYTES) {
                return Result.failure(IllegalArgumentException("Font file exceeds 8 MB limit"))
            }
            // Magic-byte validation: TTF starts with 0x00 0x01 0x00 0x00, OTF with 'OTTO'.
            if (bytes.size < 4 ||
                !((bytes[0] == 0x00.toByte() && bytes[1] == 0x01.toByte() && bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()) ||
                  (bytes[0] == 'O'.code.toByte() && bytes[1] == 'T'.code.toByte() && bytes[2] == 'T'.code.toByte() && bytes[3] == 'O'.code.toByte()))
            ) {
                return Result.failure(IllegalArgumentException("File is not a valid .ttf/.otf font (magic bytes mismatch)"))
            }
            outFile.writeBytes(bytes)
            Result.success(InstalledFont(
                name = outFile.nameWithoutExtension,
                path = outFile.absolutePath,
                sizeBytes = outFile.length()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadTypeface(font: InstalledFont): Typeface? = try {
        Typeface.createFromFile(File(font.path))
    } catch (e: Exception) { null }

    fun deleteFont(font: InstalledFont): Boolean {
        return File(font.path).delete()
    }

    fun restoreDefaults() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Computes SHA-256 of a font file (used for diagnostics / debugging). */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_FONT_BYTES = 8L * 1024 * 1024
    }
}

data class InstalledFont(
    val name: String,
    val path: String,
    val sizeBytes: Long,
)
