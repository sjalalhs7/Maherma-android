# Project status (honest)

## Done (code written, not yet compiled or run)
Voice pipeline · streaming LLM with function calling · 20 tools with confirmation tiers · reminders (exact/approx, reboot-safe) ·
tasks, notes, memory (user-controlled), activity log · Home/Conversation/Tasks/Notes/Memory/Activity/Settings screens ·
permissions center · offline fallback · launcher icon · repo replace scripts.

## Must happen before it can be called finished (needs a machine with Android Studio + a phone)
1. First Gradle sync/build: fix compile errors (unknown count; code has never been compiled).
2. Run on a real phone: mic, speech-to-text, TTS, barge-in, reminders, confirm cards.
3. Urdu / Roman Urdu quality pass on the target phone.
4. Verify the Gemini model id and function-calling replies against a live key.

## Not built (deliberately deferred; add after step 1 compiles)
Notification summaries · calendar read · web search · daily brief / follow-up scheduler · default-assistant overlay ·
wake word · bundled fonts · real glass blur · release minify rules · unit/UI tests.

## Updating GitHub so new files REPLACE old ones
GitHub's web "Upload files" only overwrites files with the SAME path and never deletes old ones.
Use tools/replace_repo.sh (Mac/Linux/Git Bash) or tools\replace_repo.bat (Windows):
    ./tools/replace_repo.sh https://github.com/USER/REPO.git main
It clones the repo, deletes everything old, copies this project in, commits and pushes.

## Build without Android Studio: GitHub Actions
Push to `main` → Actions tab → "Build Maherma". Green = download APK from the run's Artifacts (Maherma-debug-apk).
Red = open the run → Summary → "Build errors" → copy to Claude.
