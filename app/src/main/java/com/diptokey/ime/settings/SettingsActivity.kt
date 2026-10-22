package com.diptokey.ime.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diptokey.ime.DiptoKeyApp
import com.diptokey.ime.R
import com.diptokey.ime.ai.AIProviderConfig
import com.diptokey.ime.ai.ProviderType as AIProviderType
import com.diptokey.ime.fonts.FontManager
import com.diptokey.ime.suggestions.SuggestionEngine
import java.util.UUID

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsNav()
                }
            }
        }
    }
}

@Composable
private fun SettingsNav() {
    var tab by remember { mutableStateOf(SettingsTab.ONBOARDING) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = SettingsTab.values().indexOf(tab)) {
            SettingsTab.values().forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label, maxLines = 1) }
                )
            }
        }
        when (tab) {
            SettingsTab.ONBOARDING -> OnboardingScreen()
            SettingsTab.GENERAL -> GeneralScreen()
            SettingsTab.TYPING -> TypingScreen()
            SettingsTab.EDITING -> EditingScreen()
            SettingsTab.AI -> AIScreen()
            SettingsTab.CLIPBOARD -> ClipboardScreen()
            SettingsTab.FONTS -> FontsScreen()
            SettingsTab.PRIVACY -> PrivacyScreen()
        }
    }
}

enum class SettingsTab(val label: String) {
    ONBOARDING("Onboarding"),
    GENERAL("General"),
    TYPING("Typing"),
    EDITING("Editing"),
    AI("AI"),
    CLIPBOARD("Clipboard"),
    FONTS("Fonts"),
    PRIVACY("Privacy")
}

@Composable
private fun OnboardingScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Enable DiptoKey", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("DiptoKey must be enabled in Android system settings before it can type.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            // Open system keyboard settings
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Need application context to start safely
            DiptoKeyApp.instance.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text("Open system keyboard settings") }
        Spacer(Modifier.height(16.dp))
        Text("After enabling, select DiptoKey as the default input method.")
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            // Show input method picker (works only if DiptoKey is enabled)
            val imm = DiptoKeyApp.instance.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }) { Text("Show keyboard picker") }
    }
}

@Composable
private fun GeneralScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("General preferences", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("• Theme: Light / Dark / System (follows system night-mode toggle)")
        Text("• Keyboard height: Adjustable via the keyboard's own toolbar long-press")
        Text("• Haptic feedback: Toggle in Android system Sound & vibration settings")
        Text("• Sound: Toggle in Android system settings")
        Spacer(Modifier.height(16.dp))
        Text("Note: per-app theme overrides are not supported in v1.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TypingScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Typing preferences", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("• Bangla phonetic transliteration: ON by default")
        Text("• English word suggestions: ON by default")
        Text("• Next-word prediction: ON")
        Text("• Autocorrection: not enabled in v1 (conservative default)")
        Text("• Personal dictionary: tap a suggestion long-press to add")
    }
}

@Composable
private fun EditingScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Editing controls", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("• Shift: tap once for one-shot uppercase, tap again for caps lock")
        Text("• Ctrl: tap to arm for next key, tap again to cancel")
        Text("• Arrow keys: below the Enter key (← → on phone, full 4-way in Editing mode)")
        Text("• Cursor swipe: swipe horizontally across the Space key to move cursor")
        Text("• Editing mode: tap ✏ in the toolbar for arrows, copy/cut/paste, home/end, undo/redo")
        Spacer(Modifier.height(8.dp))
        Text("Note: Undo/Redo, Home/End depend on the editor app. If the editor doesn't support them, the key is a no-op.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AIScreen() {
    val app = DiptoKeyApp.instance
    val orchestrator = remember { com.diptokey.ime.ai.AIOrchestrator.get(app, app.secretStore, com.diptokey.ime.ai.ContextManager()) }
    val configs = remember { orchestrator.configs }
    var showAdd by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("AI providers", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (configs.isEmpty()) {
            Text("No providers configured. Tap 'Add provider' to add one.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(configs) { cfg ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(cfg.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("${cfg.type} — ${cfg.modelId}", style = MaterialTheme.typography.bodySmall)
                            Text(cfg.baseUrl, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    orchestrator.deleteProvider(cfg.id)
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = !showAdd }) { Text(if (showAdd) "Cancel" else "Add provider") }
        if (showAdd) AddProviderForm(onSave = { cfg, key ->
            orchestrator.saveProvider(cfg, key)
            showAdd = false
        })
    }
}

@Composable
private fun AddProviderForm(onSave: (AIProviderConfig, String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://generativelanguage.googleapis.com/v1beta") }
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("gemini-1.5-flash") }
    var isGemini by remember { mutableStateOf(true) }
    Column(modifier = Modifier.padding(8.dp)) {
        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL (HTTPS)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        Row {
            RadioButton(selected = isGemini, onClick = { isGemini = true })
            Text("Gemini")
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = !isGemini, onClick = { isGemini = false })
            Text("OpenAI-compatible")
        }
        Button(onClick = {
            val cfg = AIProviderConfig(
                id = UUID.randomUUID().toString(),
                displayName = displayName.ifBlank { "Provider" },
                type = if (isGemini) AIProviderType.GEMINI else AIProviderType.OPENAI_COMPATIBLE,
                baseUrl = baseUrl.trim(),
                apiKeyAlias = "api_key_${UUID.randomUUID()}",
                modelId = modelId.trim(),
            )
            onSave(cfg, apiKey)
        }) { Text("Save") }
    }
}

@Composable
private fun ClipboardScreen() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Clipboard", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("History is captured only when you explicitly paste through the keyboard's clipboard panel.")
        Spacer(Modifier.height(8.dp))
        Text("Tap 'Clipboard' in the keyboard toolbar to open the panel.")
        Spacer(Modifier.height(8.dp))
        Text("Settings supported: pin/unpin, delete, clear all. Auto-expiration will arrive in a future release.")
    }
}

@Composable
private fun FontsScreen() {
    val app = DiptoKeyApp.instance
    val fontManager = remember { FontManager(app) }
    val installed = remember { mutableStateListOf<com.diptokey.ime.fonts.InstalledFont>().apply { addAll(fontManager.listInstalledFonts()) } }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Custom fonts", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Imported fonts apply to the keyboard UI only. Other apps will continue using their own fonts.")
        Spacer(Modifier.height(16.dp))
        if (installed.isEmpty()) {
            Text("No custom fonts installed.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(installed) { font ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(font.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            fontManager.deleteFont(font)
                            installed.remove(font)
                        }) { Text("Delete") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Import: use 'Open file' from the toolbar after tapping the Settings gear. The keyboard file picker handles .ttf and .otf.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyScreen() {
    val app = DiptoKeyApp.instance
    val suggestionEngine = remember { SuggestionEngine.get(app) }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Clear data stored locally on this device.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { suggestionEngine.clearLearned() }) { Text("Clear learned vocabulary") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { app.secretStore.clearAll() }) { Text("Delete stored API credentials") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            com.diptokey.ime.storage.AppDatabase.get(app).clipboardDao().let { dao ->
                kotlinx.coroutines.runBlocking { dao.clearAll() }
            }
        }) { Text("Clear clipboard history") }
        Spacer(Modifier.height(16.dp))
        Text("Privacy notes", style = MaterialTheme.typography.titleSmall)
        Text("• No keylogging, ever. The IME does not record or transmit what you type.")
        Text("• No background telemetry.")
        Text("• AI requests are user-initiated. The keyboard never silently sends text to any provider.")
        Text("• API keys are stored using Android Keystore-backed encryption.")
        Text("• Password fields disable suggestions, learning, and clipboard retention.")
    }
}
