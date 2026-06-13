# Handoff – Fantasy Realms Scoring App

> ⚠️ **Der Rest dieses Dokuments ist veraltet** (Stand Phase 01). Aktueller Paketname ist
> `de.morzo.realmscore`, nicht `de.basti.fantasyrealms`. Nur dieser Status-Block wird gepflegt.

## Phasen-Status

- MVP (01–16) + Phasen 17–24 umgesetzt.
- **Spec 25.4 (Joker-Auswertung / Totenbeschwörer als Joker): ✅ erledigt** (2026-06-13).
  Totenbeschwörer ist ein echter `JokerType.NECROMANCER`, läuft durch dieselbe
  Resolve-/Optimizer-/Persistenz-Pipeline; Joker-Auflösung folgt der Berechnungsreihenfolge
  (Buch der Veränderung kann die geholte 8. Karte umfärben, Insel hebt deren Strafe auf).
  Keine DB-Migration nötig. Build + Unit-Tests grün.
- 25.1/25.2/25.3/25.7 ebenfalls erledigt (siehe git log). Offen: 25.5 (Erfassungs-Flow),
  25.6 (Sandbox-UI).

---

## Zweck dieses Dokuments (veraltet)

Übergabe an eine neue Claude-Session. Beschreibt den **Code-Stand** (Phase 01 implementiert, Build grün) und das **aktuelle Problem** (User kann die App nicht im Android-Studio-Emulator testen, weil Gradle Sync nicht durchläuft / Module nicht erkannt wird).

User wird Screenshots aus Android Studio teilen.

---

## Projekt

- **Pfad:** `C:\Users\basti\AndroidApps\FantasyRealmScoringApp`
- **Plattform:** Android, Windows 11
- **Spec-Quelle:** `specs/00-vision.md` + `specs/01-Onboarding.md` (Phasen 02–15 existieren, sind aber noch nicht dran)
- **Sprache:** Deutsch in UI und Konversation; Code-Identifier auf Englisch

---

## Was implementiert ist (Phase 01)

### Tech-Stack (siehe `gradle/libs.versions.toml`)

| Komponente | Version |
|---|---|
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Gradle | 9.4.1 |
| Compose BOM | 2026.02.01 |
| Room | 2.7.2 |
| Navigation Compose | 2.9.5 |
| DataStore Preferences | 1.1.7 |
| kotlinx-serialization | 1.8.0 (im Catalog, noch ungenutzt) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Java | 11 (compileOptions); JBR 21 für Gradle |

### Ordnerstruktur

```
FantasyRealmScoringApp/
├── build.gradle.kts                (Top-level: nur Plugin-Aliases apply false)
├── settings.gradle.kts             (rootProject = "FantasyRealmScoringApp", :app)
├── gradle.properties               (enthält android.disallowKotlinSourceSets=false – siehe unten)
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/                    (gradle-wrapper.jar aus ToolchainTest kopiert, Gradle 9.4.1)
├── gradlew / gradlew.bat
├── local.properties                (sdk.dir = C:\Users\basti\AppData\Local\Android\Sdk)
├── specs/                          (00-vision.md … 15-Polish.md – nicht ändern)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── schemas/                    (Room schema export)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/strings.xml  (alle Texte aus Spec)
        │   ├── values/themes.xml   (Theme.FantasyRealms)
        │   ├── values/colors.xml   (nur ic_launcher_background)
        │   ├── drawable/ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
        │   └── xml/{backup_rules,data_extraction_rules}.xml
        └── java/de/basti/fantasyrealms/
            ├── FantasyRealmsApp.kt
            ├── di/AppContainer.kt
            ├── data/
            │   ├── db/AppDatabase.kt
            │   ├── db/dao/ProfileDao.kt
            │   ├── db/entity/ProfileEntity.kt
            │   ├── datastore/DeviceUuidProvider.kt
            │   └── repository/ProfileRepositoryImpl.kt
            ├── domain/
            │   ├── model/Profile.kt
            │   ├── repository/ProfileRepository.kt
            │   └── util/Clock.kt
            └── ui/
                ├── MainActivity.kt
                ├── nav/{AppNavHost,Routes}.kt
                ├── onboarding/{OnboardingScreen,OnboardingViewModel}.kt
                ├── placeholder/{HomePlaceholderScreen,HomePlaceholderViewModel}.kt
                └── theme/{Color,Theme,Type}.kt
```

### Architektur-Highlights

- **Manual DI:** `AppContainer` (lazy Properties: `database`, `deviceUuidProvider`, `clock`, `profileRepository`), gehalten von `FantasyRealmsApp` (Application-Klasse, im Manifest als `android:name=".FantasyRealmsApp"` registriert)
- **ViewModels** via simple `ViewModelProvider.Factory` (kein Hilt)
- **Routing:** `splash` → check `getLocalOwner()` → `onboarding` oder `home_placeholder`; Splash und Onboarding werden jeweils aus dem Back-Stack entfernt
- **`DeviceUuidProvider`:** liest/persistiert UUID über DataStore Preferences (`device_prefs.preferences_pb`), Datei `data/datastore/DeviceUuidProvider.kt` mit `Context.devicePreferencesDataStore`
- **`ProfileRepositoryImpl.generateProfileId`** = `sha256(deviceUuid + "|" + name.lowercase()).take(32)`
- **`createOwner`** mit `require(getLocalOwner() == null)` und `require(name.trim().isNotEmpty())`
- **Owner-Default-Farbe:** `0xFF6750A4` (Material 3 Primary)
- **Room-DB:** Version 1, `exportSchema = true`, Schema-Output nach `app/schemas/` via KSP-Arg
- Alle UI-Texte aus `strings.xml`, keine Hardcoded-Strings

### Build-Status

- `./gradlew assembleDebug` ist **erfolgreich durchgelaufen** (auf Kommandozeile mit JBR 21 von Android Studio)
- APK wurde gebaut: `app/build/outputs/apk/debug/app-debug.apk` (~13 MB)
- F-Droid-Check sauber: keine `gms` / `firebase` / `mlkit` / `google-services` in den Dependencies

---

## Stolperstein: AGP 9 + KSP-Inkompatibilität

AGP 9 bringt **eingebautes Kotlin** mit (kein separates `kotlin-android`-Plugin nötig). KSP 2.2.10-2.0.2 nutzt aber noch das alte `kotlin.sourceSets`-DSL, das mit AGP 9 nicht mehr erlaubt ist.

**Workaround in `gradle.properties`:**

```
android.disallowKotlinSourceSets=false
```

Das gibt nur eine Warnung beim Build ("experimental"). **TODO für später:** sobald KSP eine AGP-9-kompatible Version (vermutlich KSP 2.2.20-2.0.5+) veröffentlicht, Flag entfernen und KSP-Version bumpen.

---

## Aktuelles Problem: Tests im Emulator scheitern

User wollte den manuellen Abschluss-Test aus `specs/01-Onboarding.md` durchführen (App im Emulator starten, "TestUser" eingeben, Kill+Restart-Verhalten prüfen), kommt aber in Android Studio nicht weiter.

### Symptome (User-Aussagen)

1. Projekt wurde in Android Studio geöffnet.
2. **Der grüne ▶ Run-Button ist ausgegraut.**
3. Im **Run-Configuration-Dropdown** (oben links, neben dem ▶) kann beim Modul-Feld **nicht `app` ausgewählt werden – nur `<no_module>`**.

### Was das bedeutet

Android Studio hat das Projekt nicht als Gradle-Multi-Modul-Projekt importiert. Entweder ist **Gradle Sync nie durchgelaufen**, **fehlgeschlagen** oder das Projekt wurde **als reiner Ordner statt als Gradle-Projekt** geöffnet.

### Was wir noch NICHT wissen (User-Screenshots benötigt)

- **Sync-Status:** Gab es einen "Sync Now" / "Sync failed" Banner oben? Steht "Indexing..." oder "Gradle Build Model..." unten in der Statusleiste?
- **Sync-Fehlertext:** Inhalt des Tabs "Sync" im unteren Tool-Window (falls Sync gestartet wurde und fehlschlug)
- **Project Tool Window links:** Haben `FantasyRealmScoringApp` und `app` ein Android-Roboter-Icon (= Gradle-Projekt erkannt) oder normale Ordner-Icons (= reiner Folder)?
- **Gradle-JDK-Einstellung:** `File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` – sollte `jbr-21` oder "Embedded JDK" sein, nicht System-JDK 8/11.
- **Eingerichteter Emulator (AVD):** im `Device Manager` schon erstellt? Welche API-Level?

### Diagnose-Pfad (in Reihenfolge)

1. **Sanity-Check: Wurde als Gradle-Projekt geöffnet?**
   - Falls die Top-Level-Ordner im Project-Tool-Window normale Ordner-Icons haben → `File → Close Project → Open` und explizit die **`settings.gradle.kts`** auswählen (nicht den Ordner).
2. **Sync manuell anstoßen:** `File → Sync Project with Gradle Files` (Elefant-mit-Pfeil-Icon)
3. **Sync-Output prüfen:** bei Fehler → Tab "Sync" unten → Text teilen
4. **Gradle JDK prüfen:** muss `jbr-21` / "Embedded" sein
5. **Falls Erst-Sync läuft:** geduldig sein – beim ersten Mal werden Gradle 9.4.1, AGP 9.2.1, Compose BOM 2026.02 etc. heruntergeladen (mehrere hundert MB), das dauert 5–15 Min je nach Internet
6. **Nach erfolgreichem Sync:**
   - Run Configuration sollte automatisch "app" anbieten; ansonsten `Edit Configurations → + → Android App → Module: app`
   - Emulator anlegen via `Tools → Device Manager → + → Phone (Pixel 7) → API 34+ System Image`
   - Emulator starten, Run-Button drücken

### Abschluss-Test, der manuell laufen soll

(Aus `specs/01-Onboarding.md`, Abschnitt "Abschluss-Test")

1. App deinstallieren (falls vorher installiert) bzw. Emulator-Daten wipen
2. App neu starten → `OnboardingScreen` muss erscheinen
3. Name "TestUser" eingeben → "Weiter" → `HomePlaceholderScreen` mit "Hallo TestUser!"
4. App-Switcher: App killen
5. App neu öffnen → direkt `HomePlaceholderScreen`, kein Onboarding

---

## Akzeptanzkriterien Phase 01 (Stand)

- [x] Projekt baut fehlerfrei mit `./gradlew assembleDebug` (Kommandozeile)
- [x] Min SDK 26, Target SDK 36, Kotlin 2.2.10+, Compose BOM 2026.02.01
- [x] F-Droid-konform (keine gms/firebase/mlkit/google-services Dependencies)
- [x] Ordnerstruktur entspricht der Spec
- [x] Alle Texte aus `strings.xml`
- [ ] **Manueller Klick-Pfad-Test im Emulator** ← blockiert durch Setup-Problem oben
- [ ] **Persistenz-Test (App-Neustart überspringt Onboarding)** ← blockiert

---

## Hinweise für die neue Session

- User ist **deutschsprachig**, bitte auf Deutsch antworten.
- User schickt **Screenshots aus Android Studio**, daraus die Diagnose lesen (Sync-Status, Fehlermeldungen, Project-Tool-Window-Icons, Run-Config-Dropdown).
- **Build-Logik bereits validiert** – Problem liegt zu ~100% in der Android-Studio-IDE-Integration (Gradle Sync, JDK-Setup, Projekt-Import-Modus), **nicht im Code**. Code nicht ohne klaren Grund anfassen.
- Falls Sync trotz allem nicht durchläuft: `gradle.properties` enthält den `disallowKotlinSourceSets=false`-Workaround – das ist Absicht (siehe Stolperstein-Abschnitt oben), nicht entfernen.
- **Spec-Dateien in `specs/` nicht editieren** – die sind die Quelle der Wahrheit für die Phasen.
