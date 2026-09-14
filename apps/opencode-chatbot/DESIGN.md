# DESIGN.md — OpenCode Chatbot

## App: OpenCode Chatbot · slug `opencode-chatbot`

### Elevator pitch
A polished, modern, fully open-source AI chatbot for Android that talks to the
user's own choice of OpenAI-compatible API providers (including Big
Pickle/OpenCode-compatible endpoints) through a clean provider abstraction.
Conversations are stored locally, API keys are held in Android secure storage,
and messages render rich markdown with syntax-highlighted code blocks. No
account, no ads, no subscriptions, no payments. Designed first for Android
8.0+ (API 26) low-RAM phones, dependency-free beyond the Android framework.

### 1. Screens

| Screen | Purpose | Key content / widgets |
|--------|---------|------------------------|
| Chat (Home, launcher) | Main chat experience: run and read conversations | Header (app title "OpenCode Chatbot", New Chat, Conversations, Settings), active conversation title, scrollable message list (user/assistant/error bubbles), message composer (multiline `EditText`, Send, Stop-while-generating), empty state prompting to start a chat |
| Conversations | List, open, rename, delete, search, organize all conversations | Title + back, search field, `ListView` of conversations (title, preview, updated date; active marked), "Delete all" action, empty state |
| Settings | Configure the AI provider, model, theme, data management | Provider form: provider preset spinner, provider name, API endpoint, API key (masked, toggle), model, temperature, max tokens, system prompt; theme toggle (light/dark/system); data actions: export conversations, import conversations, clear API keys, clear all conversations; About link |
| About | Credits, version, security notes | App name, version, short description (open-source, local-first, keys never leave the chosen endpoint), Back button |

- **Empty states**: fresh app → chat area shows "Start a new chat below"; no
  conversations → the Conversations list shows "No conversations yet"; search
  with no match shows "No matching conversations."
- **Scroll behavior**: messages auto-scroll during streaming unless the user
  scrolled up; composer stays pinned above the keyboard (`adjustResize`).
- **Device sizes**: phones, portrait-first. `LinearLayout` + weighted list so
  everything scales on narrow/small screens without custom layout logic.

### 2. Navigation
- **Launcher** opens `MainActivity` (the chat screen). No splash, no globals.
- Chat → New Chat: cleared composer + fresh empty conversation (previous
  conversation is auto-persisted).
- Chat → Conversations: opens `ConversationsActivity`; selecting a row returns
  to Chat with that conversation loaded; Back returns to Chat (active
  conversation is preserved).
- Chat → Settings: opens `SettingsActivity`; changes are applied immediately
  (provider config is read at send time). Back returns to Chat.
- Chat/Conversations/Settings → About: from Settings only (single entry point).
- **Back behavior**:
  - Chat: if the soft keyboard is open, Back first dismisses the keyboard and
    is consumed; a second Back exits (default).
  - Conversations and Settings: Back returns to Chat without data loss.
  - No confirmation dialogs on back except destructive actions (delete
    conversation, delete all, clear API keys) which always ask first.
- **Global navigation**: none required beyond the header actions — no tabs, no
  drawer (keeps the app light and single-threaded to the chat).

### 3. Visual style
- **Palette**:
  - Primary: `#4C6EF5` (indigo) — header accents, send button, active states.
  - Primary dark: `#3D56C9` — status bar.
  - Accent: `#22C55E` (green) — success/online, New Chat; danger `#E5484D`.
  - Light scheme: background `#F4F6FB`, surface `#FFFFFF`, user bubble
    `#4C6EF5` (white text), assistant bubble `#FFFFFF` (dark text), text
    primary `#1C2333`, text secondary `#5B6B7F`, code background `#0F172A`.
  - Dark scheme: background `#0F1322`, surface `#1A2036`, user bubble
    `#4C6EF5`, assistant bubble `#232B45` (light text), text primary
    `#E7EAF3`, text secondary `#9AA6C4`, code background `#0B0F1C`.
- **Typography**: system font (Roboto). Header 18sp (bold), conversation title
  15sp (semi-bold), message body 15sp (1.35 line spacing), metadata/caption
  12sp, code 13sp monospace (`monospace` family). All `sp`.
- **Spacing/layout**: 14dp page margins, 8dp vertical gaps, assistant message
  full-width with 12dp corners, user message right-aligned 12dp corners, 48dp
  tap targets on all controls.
- **Tone**: serious modern AI assistant — calm, high-contrast, clean; flat
  colors, generous whitespace, subtle rounded corners, no heavy shadows.
- **Light/dark**: both themes implemented as two framework styles
  (`Theme.ChatLight`, `Theme.ChatDark`) plus a "system" option; colors are
  theme attributes (`?attr/…`) so every screen flips instantly and code-drawn
  surfaces (bubbles) follow the same palette.
- **Rationale**: dark, code-adjacent palette with a single high-contrast
  primary reads like a developer tool (OpenCode-style) rather than a toy.

### 4. Interactions
- **Send**: posts the composer text as a user message, appends it, starts a
  streaming request against the configured provider, and shows a Stop button.
- **Enter/newline**: Enter inserts a newline (multiline composer); Send is an
  explicit button. The Send button and keyboard IME stay within the resize
  area — nothing hides behind the keyboard.
- **Stop**: replaces Send while generating; cancels the stream and preserves
  the partial assistant response as a completed message.
- **Message actions (long-press bubble)**: user bubble → *Edit & resend*,
  *Copy*; assistant bubble → *Copy*, *Share*, *Regenerate*, and on error
  bubbles → *Retry*, *Copy*. Regenerate re-runs the last user message;
  Edit & resend loads the message text into the composer in "edit" mode and
  sends it as a replacement.
- **Copy / Share**: frame-less — Copy writes the plain-message text (including
  the raw markdown) to the clipboard with a toast; Share fires an
  `ACTION_SEND` chooser.
- **Autoscroll**: during streaming the list follows the tail automatically;
  scrolling up pauses follow, a "jump to latest" affordance appears, and
  tapping it resumes follow.
- **Settings validation**: endpoint must be an `http(s)` URL; temperature
  clamped to 0–2; max tokens clamped to 1–32 768; empty model falls back to
  the preset's model; saving requires a provider name + endpoint. Invalid
  input is surfaced inline and not saved.
- **API key handling**: stored via `AndroidKeyStore` AES-GCM encrypted in
  `SharedPreferences`; never written to chat, logs, or exported JSON. A show
  toggle reveals the key in the field. "Clear API keys" wipe all stored keys
  after confirmation.
- **Network errors**: request failures present an in-chat error bubble with a
  Retry action; partial streamed text is kept; no crash, no silent failure.
- **Offline**: browsing/editing conversations works fully offline (local
  store); a network error surfaces only when sending/receiving.
- **Accessibility**: all buttons ≥48dp, string labels on every control,
  content descriptions on icon-only actions, system font scaling respected.

### 5. App-icon concept
- **Symbol**: a rounded chat bubble containing an indigo code caret `</>` —
  mapping the "code + conversation" character of the app.
- **Colors**: primary `#1565C0` background tile (matches AppFactory family),
  white bubble, indigo `#4C6EF5` caret with a white dot spark.
- **Background treatment**: solid blue rounded tile; adaptive foreground is a
  vector; legacy densities rendered by `tools/gen-icons.py`.
- **Rendering**: `<adaptive-icon>` for API 26+ (background color + vector
  foreground) and generated PNGs for the five legacy densities.

## Delivery checklist
- [x] Every screen covered (Chat, Conversations, Settings, About)
- [x] Navigation fully specified (forward/back, keyboard-aware Back)
- [x] Visual style concrete (hex values, sizes, light/dark)
- [x] Interactions and feedback described (send/stop/actions/validation/errors)
- [x] App-icon concept concrete and implementable as drawables