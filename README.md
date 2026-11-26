# Maherma – voice assistant manager (Android)

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore. minSdk 29, target 35.

## Run
1. Open the folder in Android Studio (JDK 17). It creates the Gradle wrapper. Sync.
2. Run on a real device (speech recognition and TTS are unreliable on emulators).
3. Settings → paste a Gemini API key; check the model id (default `gemini-2.5-flash`).
4. Without a key, basic commands still work: open X, timer N min, remind me to X in N min, note X, flashlight on/off, call X.

NOT compiled where it was authored (no network). Expect small fixes on first sync; see docs/DECISIONS.md.

## Structure
```
com.maherma.app
├─ MahermaApp, MainActivity
├─ di/AppModule                      Json, OkHttp, Room, DAOs
├─ domain/model/Models               PhoneAction, ChatItem, ActionStatus
├─ voice/                            Audio pipeline + session
│   ├─ SessionController             state machine IDLE→LISTENING→THINKING→ACTING→SPEAKING, barge-in, hands-free
│   ├─ AndroidStt                    SpeechRecognizer as Flow (partials, RMS, on-device when available)
│   ├─ TtsQueue                      sentence-queued TextToSpeech, audio focus, stop on interrupt
│   └─ BargeInDetector               talk-over detection, only when echo cancellation exists
├─ orchestrator/
│   ├─ Orchestrator                  context → streamed LLM → tool calls (≤4 hops) → persisted reply
│   ├─ Prompts, SentenceChunker
│   └─ LocalRouter                   offline / no-key commands
├─ llm/GeminiClient                  SSE streaming + function calling (provider swap point)
├─ tools/
│   ├─ ToolRegistry                  20 tools with JSON schemas and risk tiers
│   ├─ ToolRouter                    tier policy, confirmation cards, permission/ambiguity handling, audit
│   └─ Tools                         ToolDef, ToolResult, schema helpers
├─ device/                           ActionExecutor, ContactResolver, AppResolver, ReminderScheduler/Receivers, NetworkMonitor
├─ data/{db,prefs,repo}              Room (threads, messages, tasks, notes, memory, audit), Keystore-encrypted key
└─ ui/
    ├─ home        orb + live caption + today strip + pending confirmations + text fallback
    ├─ conversation  full thread, cards, retry, history dialog
    ├─ manage      tasks, notes, memory (edit/pin/disable/delete/forget all), activity log
    ├─ settings    provider, voice, permissions center, background health, privacy, wipe
    └─ components  VoiceOrb, GlassCard, bubbles, ActionCard, InfoCard
```

## One turn
mic → AndroidStt (partials) → SessionController → Orchestrator → GeminiClient (stream)
→ text deltas → SentenceChunker → TtsQueue (speech starts at first sentence)
→ tool call → ToolRouter: T0/T1 run now · T2/T3 persist a confirm card (tap required) → result → model continues
