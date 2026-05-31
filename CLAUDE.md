# CLAUDE.md — Project notes for Claude Code

Auto-loaded every session. Keep concise; HANDOFF.md and `specs/00-vision.md` carry the broader context.

## Project

- **Path:** `C:\Users\basti\AndroidApps\FantasyRealmScoringApp` (Windows 11)
- **Stack:** Android, Kotlin 2.2.10, AGP 9.2.1, Gradle 9.4.1, Compose BOM 2026.02.01, Room 2.7.2, KSP 2.2.10-2.0.2 — see `gradle/libs.versions.toml`.
- **JDK used by Gradle:** JBR 21 bundled with Android Studio.
- **Specs:** numbered phases in `specs/` are the source of truth. Don't edit them.
- **Conversation language:** German (Code-Identifier auf Englisch).

## Building from this environment (verified)

Plain `./gradlew` via Bash **fails** here — `JAVA_HOME` is not set in the shell:

```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

Use PowerShell with `JAVA_HOME` set inline. The two commands that worked in this session:

```powershell
# Fast iteration — Kotlin-only compile (~1 min cold, seconds incremental)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; `
  & "C:\Users\basti\AndroidApps\FantasyRealmScoringApp\gradlew.bat" `
  :app:compileDebugKotlin --console=plain 2>&1 | Select-Object -Last 80
```

```powershell
# Full debug APK — needed to actually validate end-to-end (~2 min)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; `
  & "C:\Users\basti\AndroidApps\FantasyRealmScoringApp\gradlew.bat" `
  :app:assembleDebug --console=plain 2>&1 | Select-Object -Last 30
```

Notes:
- Run via the **Bash tool** (not PowerShell tool) only if you first export `JAVA_HOME` — otherwise prefer the PowerShell tool with the inline `$env:JAVA_HOME=...; & "...\gradlew.bat" ...` form.
- `--console=plain` keeps output diff-friendly.
- `2>&1 | Select-Object -Last N` keeps the tool output under the token cap. Bump N (60/80/200) if a stack trace gets cut off.
- For a faster correctness check use `:app:compileDebugKotlin`. Use `:app:assembleDebug` when you need to confirm resources, KSP/Room, and packaging together.
- F-Droid-Check (must return nothing): `gradlew.bat :app:dependencies` piped through `Select-String -Pattern "(gms|firebase|mlkit|google-services)"`.

## Manual UI testing (Emulator)

This session did **not** verify behavior in an emulator — only build success. For golden-path UI tests the user runs the APK via Android Studio's Run button on an AVD. Spec acceptance checklists (e.g. `specs/02-Hauptmenue.md` §Akzeptanzkriterien) describe the click-paths; ask the user to walk them after a phase is built. See HANDOFF.md for the Gradle-Sync / Run-Configuration troubleshooting list if the IDE side regresses.

## Known build pitfalls (already worked around)

- **AGP 9 + KSP 2.2.10-2.0.2 conflict:** `gradle.properties` sets `android.disallowKotlinSourceSets=false` — keep it until KSP releases an AGP-9-compatible version. The warning during compile is expected.
- **Material icons:** `Icons.Default.*` requires `androidx-compose-material-icons-extended` (declared in `libs.versions.toml`, wired into `app/build.gradle.kts` in Phase 02). `Icons.Filled.List` is deprecated → use `Icons.AutoMirrored.Filled.List`.

## Permissions allowlist (`.claude/settings.local.json`)

The local allowlist already covers the build commands above with wildcards, plus `Bash(./gradlew *)`, `Bash(rm *.kt)`, `Bash(ls *)`.

**Standing instruction from the user:** any time a build / test / development command triggers a permission prompt and the user approves it, also write a matching rule into `.claude/settings.local.json` so the same kind of command doesn't re-prompt next time. Prefer one broad wildcard pattern (e.g. `PowerShell(<prefix> *)`, `Bash(./gradlew *)`) over stacking multiple literal entries that only differ in tail arguments. Do not silently allow-list one-off destructive commands the user explicitly only approved for that single invocation — only commands that fit a recurring dev/test pattern.
