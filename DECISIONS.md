# Problems found → what was decided

Legend: SOLVED = fixed in code · MITIGATED = reduced, not removable · LIMIT = platform fact, cannot be solved by us.

| # | Problem (from the Maherma spec / Halcyn build) | Resolution | Status |
|---|---|---|---|
| 1 | Open questions: min Android, store vs sideload, key model, wake word | minSdk 29 (settings panels, better SpeechRecognizer), target 35; Play-compatible but sideload-first; bring-your-own key, encrypted with Keystore; wake word deferred | Decided |
| 2 | Wake word needs hardware hotword path (privileged) | Not built. Push-to-talk + in-app hands-free. SessionController is app-scoped so a VoiceInteractionService / foreground mic service can drive it later | LIMIT |
| 3 | Background mic start restrictions (Android 12+/14) | No background mic anywhere: mic opens only from a visible activity after a tap | SOLVED by design |
| 4 | Barge-in could hear Maherma's own voice | Voice barge-in only when AcousticEchoCanceler exists; otherwise orb tap interrupts. Setting shows why it's off | MITIGATED |
| 5 | Latency | Streamed LLM, sentence-chunked TTS, ≤24 turns + ≤5 memories in context, on-device STT when available. Not measured | MITIGATED |
| 6 | Intent routing was a separate JSON call (Halcyn): extra round-trip, brittle | Replaced by native function calling in one streamed loop (≤4 hops) | SOLVED |
| 7 | Gemini 3-style models need thought signatures echoed with function calls | Function-call parts are echoed back verbatim (Part.Raw) | SOLVED (untested) |
| 8 | Silent sends/calls | T2/T3 tools only create a persisted confirm card; tap required; expires in 10 min; messages open the target app with a draft | SOLVED |
| 9 | Pending actions lost on process death | Cards + payloads live in Room | SOLVED |
| 10 | Exact alarms need special permission | Uses exact if allowed, else approximate alarm with honest wording; Settings deep-links; reboot + permission-change receiver re-arms | MITIGATED |
| 11 | OEM battery killers | No always-on service. Reminders use AlarmManager. Settings links to battery page | MITIGATED |
| 12 | Interrupted replies lost | Partial reply persisted; history keeps it | SOLVED |
| 13 | Permission UX | Reason dialog before the mic prompt; per-card Allow for contacts; Permissions Center; denied → text fallback + Settings link | SOLVED |
| 14 | Memory could save sensitive things silently | memory_save: explicit non-sensitive → saved; everything else asks. Edit/pin/disable/delete/forget-all; master off switch | SOLVED (keyword heuristic for "sensitive") |
| 15 | No audit of what leaves the device | Activity screen logs each LLM send (counts) and every action | SOLVED |
| 16 | Offline | NetworkMonitor, offline orb state, LocalRouter for basic commands | SOLVED (basic set only) |
| 17 | Urdu / Roman Urdu quality | Language chips, on-device option, TTS availability notice. Real quality depends on device engines | LIMIT / test on device |
| 18 | Halcyn minify/serialization risk | Release minify stays off until keep-rules are added | Open |

## Not built yet (documented in the spec, deliberately deferred)
Notification summaries (needs NotificationListenerService), calendar read, web search, daily brief and follow-up scheduler, VoiceInteractionService assistant overlay, wake word, bundled fonts, real glass blur.

## Known risks in this code
- Never compiled or run. Library versions are from training knowledge; bump if Gradle complains.
- Default model id `gemini-2.5-flash` may be retired; it is editable in Settings.
- `EXTRA_SPEECH_INPUT_*_SILENCE_*` are hints; some recognizers ignore them.
- AEC may not actually cancel TTS on every device; if barge-in false-triggers, switch it off in Settings.
