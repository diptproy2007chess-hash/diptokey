# DiptoKey Privacy Policy

DiptoKey is designed with privacy as a primary engineering requirement. This
document describes exactly what the keyboard does and does not do with your
data.

## 1. What we collect

**Nothing by default.** DiptoKey has no analytics, no telemetry, no remote
logging, and no account system. The keyboard has no way to identify you.

When you type, your text goes **only** to the app you're typing into — the
same way any system keyboard works. DiptoKey never transmits, stores, or
processes your typing remotely.

## 2. What is stored locally on your device

These are stored **only** on your device, in app-private storage. They are
never uploaded.

- **Learned vocabulary** (DataStore): the most recent words you typed, with
  frequency counts, used to improve suggestions. Disabled in password fields.
- **Clipboard history** (Room DB): clipboard entries you explicitly capture
  or that result from pasting through the keyboard's own panel.
- **AI provider configuration** (DataStore): provider name, base URL, model ID.
- **API keys** (`EncryptedSharedPreferences`, Android Keystore-backed
  AES-256): the API key you entered for each AI provider.

You can clear any of these from Settings → Privacy at any time.

## 3. What is sent to AI providers

DiptoKey **never** sends data to any AI provider without an explicit user
action. There is no background AI, no auto-AI on every keystroke.

When you tap the AI button and submit an instruction:

- The instruction text you typed.
- The source text you explicitly added (selected text, pasted text, or
  snippets you added to the context).
- Any context items you explicitly added (previous responses, snippets).

What is sent is shown in the AI Context panel before you submit. You can
remove items, clear all, or cancel entirely.

**Important:** when you submit an AI request, your text leaves your device
and goes to the AI provider you configured. The provider's own retention and
privacy policies apply once the data reaches them. DiptoKey cannot control
what they do with it.

## 4. Sensitive input handling

When the focused field is detected as a password, numeric password, or has
the `TYPE_TEXT_FLAG_NO_SUGGESTIONS` flag, DiptoKey:

- Disables the suggestion strip.
- Disables learning (no entries are written to the user dictionary).
- Disables clipboard retention.
- Treats the input as a stream of characters committed directly to the app.

DiptoKey never reads password fields, banking apps, or payment credentials
in any way other than the standard IME text-commit path that the Android OS
itself enforces.

## 5. Network access

- The INTERNET permission is required for AI requests.
- **No** network request is made during normal typing.
- The only time DiptoKey makes a network request is when you tap "Generate"
  (or similar AI action) and the request goes to the provider you
  configured.
- AI requests use HTTPS. Plain HTTP is rejected unless the host is
  `localhost` or `127.0.0.1` (for local-development LLM servers).

## 6. API key security

- API keys are stored using `EncryptedSharedPreferences`, which uses
  Android Keystore-backed AES-256.
- Keys are never written to logs.
- Keys are never included in crash reports (we don't ship a crash reporter).
- Keys are only sent to the configured AI provider over HTTPS, in the
  `Authorization` header.

**Caveat:** encrypted local storage protects against casual extraction
(e.g. `adb backup`). On a compromised / rooted device, an attacker with
sufficient privileges can read the secrets the same way DiptoKey can. This
is the standard mobile-app threat model and cannot be improved without
introducing a server-side vault, which we deliberately do not do.

## 7. What DiptoKey does NOT do

- No keylogging.
- No background clipboard polling.
- No analytics / telemetry.
- No advertising.
- No mandatory account registration.
- No automatic upload of clipboard history.
- No automatic upload of typed text.
- No silent routing of AI requests through a different provider than the one
  you selected.
- No collection of contacts, location, microphone, camera, or any other
  sensitive data.

## 8. Permissions used

| Permission | Why |
|---|---|
| `INTERNET` | AI provider requests (user-initiated only) |
| `ACCESS_NETWORK_STATE` | Detect connectivity before AI requests |
| `VIBRATE` | Key-press haptic feedback |
| `BIND_INPUT_METHOD` | Required by Android for any IME service |

That's all. No `READ_CONTACTS`, no `READ_SMS`, no `ACCESS_FINE_LOCATION`, no
`RECORD_AUDIO`, no `CAMERA`.

## 9. Children's privacy

DiptoKey is a general-purpose keyboard. It does not knowingly collect any
personal information from anyone, including children. There is no account
system, no profile, no targeted content.

## 10. Changes to this policy

If we change what DiptoKey collects or how it works, we will update this
document in the same repository. The commit history of `PRIVACY.md` is the
canonical record of changes.

## 11. Contact

This is an open-source project. Open an issue at the repository to ask
questions or report privacy concerns.
