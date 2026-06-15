# Build & Tooling

## Tech-Stack (Ist-Stand)

Quelle der Wahrheit: `gradle/libs.versions.toml` + `app/build.gradle.kts`.

| Komponente | Version |
|---|---|
| Kotlin | 2.2.10 |
| AGP | 9.2.1 |
| Gradle | 9.4.1 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.02.01 |
| Room | 2.7.2 |
| Java / Kotlin jvmTarget | 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / **29** |
| JDK für Gradle | JBR 21 (mit Android Studio gebündelt) |

- **applicationId / namespace:** `de.morzo.realmscore`, Debug-Variante `de.morzo.realmscore.debug`.
- **versionName / versionCode:** `1.1.0` / `2`.
- **minSdk = 29** (war 26) — wegen automatischer Silbentrennung (`Hyphens.Auto`, ab API 29).

## Build-Kommandos

`JAVA_HOME` wird über den `env`-Block in `.claude/settings.local.json` gesetzt — **nicht** inline.
Über das **PowerShell-Tool** (läuft schon im Projekt-Root):

```powershell
# Schneller Kotlin-Compile (~1 min kalt)
./gradlew.bat :app:compileDebugKotlin --console=plain 2>&1 | Select-Object -Last 80

# Voller Debug-APK (~2 min) — validiert Ressourcen, KSP/Room, Packaging
./gradlew.bat :app:assembleDebug --console=plain 2>&1 | Select-Object -Last 30

# Unit-Tests
./gradlew.bat :app:testDebugUnitTest --console=plain 2>&1 | Select-Object -Last 40
```

> ⚠️ Nicht die alte Inline-Form `$env:JAVA_HOME=...; & "C:\...gradlew.bat" ...` benutzen — die
> Allowlist-Regeln matchen dann nicht und es kommt zu Re-Prompts. Siehe `CLAUDE.md` und das Memo
> `feedback_gradle_invocation_permissions`.

## F-Droid-Konformität (harte Anforderung)

Keine Google Play Services, kein Firebase, **kein ML Kit**, kein proprietäres Tracking. `app/build.gradle.kts`
setzt zusätzlich `dependenciesInfo { includeInApk = false; includeInBundle = false }`.

Check (muss **nichts** zurückliefern):

```powershell
./gradlew.bat :app:dependencies | Select-String -Pattern "(gms|firebase|mlkit|google-services)"
```

## Bekannte Build-Stolpersteine (bereits umgangen)

- **AGP 9 + KSP-Inkompatibilität:** `gradle.properties` setzt `android.disallowKotlinSourceSets=false`.
  Erzeugt nur eine Warnung. Erst entfernen, wenn KSP eine AGP-9-kompatible Version bringt.
- **Material Icons:** `Icons.Default.*` braucht `androidx-compose-material-icons-extended`.
  `Icons.Filled.List` ist deprecated → `Icons.AutoMirrored.Filled.List`.

## Manuelles UI-Testing

Geräte-/Emulator-Tests macht der Nutzer selbst (APK via Android-Studio-Run-Button). Diese Umgebung
verifiziert nur den Build und das Durchdenken der Akzeptanzkriterien — siehe Memo
`feedback_no_emulator_driving` und `CLAUDE.md`.
