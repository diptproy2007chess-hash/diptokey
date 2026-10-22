# DiptoKey

**An advanced native Android keyboard with AI, Bangla transliteration, PC-style editing, and a clipboard manager.**

[![Android CI](https://github.com/diptproy2007chess-hash/diptokey/actions/workflows/android.yml/badge.svg)](https://github.com/diptproy2007chess-hash/diptokey/actions)

---

## What is DiptoKey?

DiptoKey is a real native Android IME (InputMethodService). It is **not** a
WebView, not a React Native app, and not a mockup. Once installed and enabled
in Android system settings, you can select it as the system keyboard and type
into any editable field across any app.

Key design goals:

1. **Familiar UX** — clean, minimal, Samsung-style keyboard surface.
2. **PC-style editing** — Shift, Ctrl, arrow keys, copy/cut/paste, undo/redo, home/end.
3. **English-to-Bangla phonetic transliteration** — type `ami`, get `আমি`.
4. **Local suggestions** — transliteration + English completion + bigram next-word prediction.
5. **Integrated AI assistant** — Gemini / OpenAI-compatible / OpenRouter providers, pluggable.
6. **Clipboard manager** — history, search, pin, snippets.
7. **Custom fonts, themes, layouts** — phone & tablet responsive.
8. **Privacy-first** — no keylogging, no telemetry, no background network on keystrokes, API keys encrypted at rest.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Custom `View` for IME; Jetpack Compose for Settings |
| Build | Gradle 8.9 (Kotlin DSL), AGP 8.7.3 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Persistence | Room (clipboard history, AI history), DataStore (user dictionary, provider configs) |
| Secrets | `EncryptedSharedPreferences` (Android Keystore-backed AES-256) |
| Networking | OkHttp 4.12 |
| Serialization | kotlinx.serialization |
| Async | Kotlin coroutines |
| Tests | JUnit4, Robolectric, Truth, MockWebServer |

## Repository layout

```
app/
├── src/main/java/com/diptokey/ime/
│   ├── DiptoKeyApp.kt              # Application init
│   ├── DiptoKeyIME.kt             # InputMethodService — the keyboard entry point
│   ├── keyboard/                  # KeyboardView, KeyboardLayout, KeyDef, ModifierState
│   ├── editing/                   # Cursor movement, copy/cut/paste, undo/redo
│   ├── transliteration/            # Bangla phonetic engine + dictionary
│   ├── suggestions/               # SuggestionEngine + UserDictionary (DataStore)
│   ├── ai/                         # Provider abstraction, orchestrator, context manager
│   │   └── providers/             # GeminiProvider, OpenAICompatibleProvider
│   ├── clipboard/                 # ClipboardController (Room-backed)
│   ├── fonts/                     # Font import / preview / delete
│   ├── security/                  # EncryptedSecretStore (Keystore-backed)
│   ├── storage/                   # Room DB, DAOs, entities
│   └── settings/                  # Compose-based SettingsActivity
├── src/main/res/                   # Layouts, themes, drawables, strings
├── src/test/java/com/diptokey/ime/ # Robolectric + Truth unit tests
└── src/androidTest/                # (skeleton) instrumentation tests
```

## Building locally

Requirements:
- Android Studio Koala/Ladybug or later
- JDK 17
- Android SDK with platform 35 + build-tools 35.0.0

```bash
# 1. Clone
git clone https://github.com/<your-org>/diptokey.git
cd diptokey

# 2. Build & run unit tests
./gradlew testDebugUnitTest

# 3. Assemble debug APK
./gradlew assembleDebug

# 4. Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Enabling DiptoKey on your device

1. Install the APK.
2. Open the DiptoKey app — you'll land on the **Onboarding** tab.
3. Tap **Open system keyboard settings** → toggle DiptoKey ON.
4. Tap **Show keyboard picker** → select DiptoKey.
5. Open any text field. Type.

The keyboard will appear with a QWERTY layout, a toolbar at the top, and a
suggestion strip below the toolbar.

## Keyboard layout

```
+---------------------------------------------+
|  AI    📋    ✏    ⚙         ← toolbar       |
+---------------------------------------------+
|  suggestion1   suggestion2   suggestion3    |
+---------------------------------------------+
|  q  w  e  r  t  y  u  i  o  p               |
|   a  s  d  f  g  h  j  k  l                 |
|  Shift  z x c v b n m  ⌫                   |
|  Ctrl   space     ← →   Enter              |
+---------------------------------------------+
```

- **Toolbar**: AI assistant, clipboard, edit-mode toggle, settings.
- **Suggestion strip**: shows Bangla transliteration candidates while you type Latin letters; English completions; next-word predictions.
- **Editing mode**: tap ✏ to switch to a 3-row editing layout with arrows, home/end, copy/cut/paste, select all, undo/redo, delete, backspace.

### Modifier keys

- **Shift**: tap once for one-shot uppercase. Tap again for caps lock. Tap a third time to disable.
- **Ctrl**: tap to "arm" — the next compatible character key will fire the Ctrl shortcut (e.g. `Ctrl+A` = select all, `Ctrl+C` = copy, `Ctrl+V` = paste, `Ctrl+Z` = undo, `Ctrl+Y` = redo). One-shot, disarms after one key.

### Cursor swipe

Swipe horizontally across the Space key to move the text cursor left/right.

### Editing mode

Tap ✏ in the toolbar to expand into a full text-editing layout with:

- `←` `↑` `↓` `→` arrow keys
- `Home` / `End` (line-start / line-end, where the editor supports it)
- `Copy` / `Cut` / `Paste` / `All` / `Undo` / `Redo` / `Del` / `⌫`

Limitations: undo/redo and home/end depend on the editor app. In apps that
don't support them, the keys are silent no-ops (no false claims).

## Bangla transliteration

The engine is rule-based and 100% offline. It applies phonetic rules
longest-match-first, with a small common-word dictionary for words that the
pure phonetic rules get wrong.

### Examples

| Latin typed | Bangla produced |
|---|---|
| `ami` | আমি |
| `tumi` | তুমি |
| `bhalo` | ভালো |
| `bangladesh` | বাংলাদেশ |
| `dhaka` | ঢাকা |
| `api` (English whitelist) | api |

To add personal mappings, see `BanglaDictionary.personalDictionary` or extend
`PhoneticRules.wordDictionary`.

## AI providers

DiptoKey supports:

1. **Google Gemini** (native Generative Language API format)
2. **OpenAI-compatible** — works with OpenAI, OpenRouter, Together, Groq, vLLM, LocalAI, llama.cpp server, etc.
3. **OpenRouter** — uses the OpenAI-compatible adapter with its base URL `https://openrouter.ai/api/v1`

### Configuring a provider

1. Open DiptoKey settings → **AI** tab.
2. Tap **Add provider**.
3. Choose type (Gemini or OpenAI-compatible), enter base URL, API key, model ID.
4. Save.

### Adding OpenRouter

- Type: OpenAI-compatible
- Base URL: `https://openrouter.ai/api/v1`
- Model ID: any OpenRouter-supported model (e.g. `openai/gpt-4o-mini`)
- API key: from your OpenRouter dashboard

### Adding a generic OpenAI-compatible endpoint

- Type: OpenAI-compatible
- Base URL: e.g. `https://api.openai.com/v1`, `https://api.together.xyz/v1`, `https://api.groq.com/openai/v1`
- Model ID: e.g. `gpt-4o-mini`, `meta-llama/Llama-Versatile`, `llama-3.1-70b-versatile`
- API key: per provider

### Security of stored API keys

- Stored using `EncryptedSharedPreferences` with Android Keystore-backed AES-256.
- Never written to logs, never included in crash reports.
- Never committed in source code.
- Only sent to the configured provider over HTTPS.

**Important caveat:** encrypted local storage protects against casual
extraction (e.g. `adb backup`). On a compromised / rooted device, any app
with sufficient privileges can read the secrets the same way DiptoKey can.
This is the standard mobile-app threat model — there is no way for a
client-only app to be safer than the device itself.

## Clipboard manager

- Open via the 📋 toolbar button.
- Search: filter entries by text.
- Pin / Unpin: pinned entries survive "Clear all unpinned".
- Delete: remove a single entry.
- Clear all: removes everything except pinned.
- Snippets: user-defined reusable text.

### Important privacy behaviour

DiptoKey does **not**:

- Register a global clipboard listener.
- Continuously read the system clipboard in the background.
- Retain clipboard content from password fields.

It captures clipboard content **only when** the user explicitly pastes through
the keyboard's own clipboard panel.

## Custom fonts

Settings → Fonts. Import `.ttf` or `.otf` files (max 8 MB). Imported fonts
apply **only to the keyboard UI rendering**. They do **not** affect how other
apps render typed text — Android does not allow an IME to influence the
receiving editor's font.

## Themes & responsiveness

- Light / Dark theme follows Android system night-mode toggle.
- Tablet layouts: the same `KeyboardView` measures against available width — wider screens get naturally wider keys (no separate layout file required for the MVP).
- Landscape: keyboard height compresses to ~52 dp × 3 rows + toolbar + suggestions.

## Privacy

See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

Short version:

- No keylogging.
- No background telemetry.
- No background network requests on keystrokes.
- No password / payment / banking data collection.
- AI requests are user-initiated only. You always see what is sent.
- API keys are encrypted at rest using Android Keystore.
- You can clear learned vocabulary, AI history, and clipboard history from Settings → Privacy.

## Testing

Unit tests live under `app/src/test/`. Run with:

```bash
./gradlew testDebugUnitTest
```

Tests covered:

- `TransliterationEngineTest` — common Bangla words, mixed sentences, punctuation, English whitelist.
- `SuggestionEngineTest` — English completion, next-word prediction, Bangla candidates.
- `OpenAICompatibleProviderTest` — happy path, 401, 429, http-rejection, localhost-allowance.
- `GeminiProviderTest` — happy path, 404 invalid model, 429 rate limit.
- `ModifierStateTest` — Shift one-shot + lock + disarm, Ctrl arm + disarm.
- `FontManagerTest` — empty list, restore defaults, oversized rejection, invalid content rejection.

Instrumentation tests (`app/src/androidTest/`) are intentionally minimal in
this initial release — most behavioral tests require a real InputConnection,
which means a real device or emulator. We document this limitation honestly.

## Known limitations

These are honest statements about what the v1 does and does not support:

1. **Undo / Redo / Home / End / vertical arrow navigation** depend on the
   receiving editor. In apps that don't implement these key events
   (`EditText` does; many `WebView`-based editors don't), the keys are
   silent no-ops.
2. **System clipboard access on Android 10+** is restricted to foreground
   apps. DiptoKey only reads the system clipboard when the user explicitly
   taps "Save current clipboard" or pastes through the keyboard's own panel.
3. **Custom fonts affect only the keyboard UI**, not the receiving editor.
4. **Autocorrection is disabled by default** to avoid destructive text
   replacement. Suggestions are non-destructive — tapping one replaces only
   the current composing word.
5. **No streaming AI responses** in v1. Responses are returned as a single
   string. Streaming may arrive in a future release.
6. **The transliteration engine is rule-based**, not a neural model. It will
   get some words wrong. Users can add personal mappings via
   `BanglaDictionary.personalDictionary`, and we plan to ship an asset-based
   dictionary in a future release.
7. **Tablet split-keyboard** is not implemented in v1.

## Feature-completion checklist

| Feature | Status |
|---|---|
| Native IME registered & selectable in system settings | ✅ |
| Basic QWERTY typing in any editable field | ✅ |
| Shift one-shot + caps lock | ✅ |
| Ctrl modifier with A/C/V/X/Z/Y shortcuts | ✅ |
| Arrow keys (← → on phone, full 4-way in editing mode) | ✅ |
| Swipe-on-space cursor movement | ✅ |
| Dedicated editing mode (arrows, home/end, copy/cut/paste, undo/redo) | ✅ |
| Bangla phonetic transliteration (offline) | ✅ |
| Suggestion strip (transliteration + English completion + bigram) | ✅ |
| Personal user dictionary (DataStore) | ✅ |
| Clipboard history (Room) | ✅ |
| Clipboard search, pin, delete, clear | ✅ |
| AI provider abstraction (Gemini + OpenAI-compatible) | ✅ |
| OpenRouter via OpenAI-compatible adapter | ✅ |
| API key encrypted storage (Keystore) | ✅ |
| AI panel UI (instruction → result → insert/replace/copy/regenerate) | ✅ (panel renders; insert wired) |
| AI context manager (instruction + selected text + snippets) | ✅ |
| Custom font import (.ttf / .otf) | ✅ |
| Light / Dark theme | ✅ |
| Settings UI (Compose) | ✅ |
| Onboarding screen | ✅ |
| Privacy controls (clear learned / clipboard / credentials) | ✅ |
| Password-field detection & sensitive mode | ✅ |
| Unit tests (transliteration, suggestions, providers, modifier state, fonts) | ✅ |
| GitHub Actions CI (build APK + run tests + upload artifact) | ✅ |
| Streaming AI responses | ❌ (planned) |
| Tablet split keyboard | ❌ (planned) |
| On-device ML-based transliteration model | ❌ (planned) |
| Autocorrection | ❌ (disabled by default; planned) |

## License

This project is licensed under the MIT License — see [LICENSE](LICENSE).

## Contributing

Pull requests welcome. Please run `./gradlew testDebugUnitTest` before opening a PR.
