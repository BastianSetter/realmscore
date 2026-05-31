# Phase 16 – Release-Vorbereitung (Code-Seite)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Der MVP ist fertig gebaut und funktioniert. Diese Phase macht die **Code-Seite** release-fertig fuer eine F-Droid-Veroeffentlichung. Die manuellen Schritte (GitHub-Repo, F-Droid-Einreichung) erledigt der User separat anhand einer eigenen Anleitung.

---

## Kontext (kurz)

Die App soll auf **F-Droid** veroeffentlicht werden unter dem Namen **RealmScore**. Dafuer muessen mehrere Code- und Konfigurationsaenderungen gemacht werden:
- Package-Name / applicationId von `de.basti.*` auf `de.morzo.realmscore`
- App-Name auf "RealmScore"
- Version 1.0.0 (versionCode 1)
- GPL-3.0-Lizenz
- Release-Build-Konfiguration (Minify + ProGuard)
- F-Droid-Metadaten-Struktur (Fastlane)
- README

**Wichtig:** F-Droid baut und signiert die App selbst. Es darf **kein** Keystore, **keine** Signing-Config mit echten Schluesseln und **keine** `local.properties` ins Repository.

Volle Vision: siehe `00-vision.md`.

---

## Release-Eckdaten (verbindlich)

| Punkt | Wert |
|-------|------|
| App-Name (sichtbar) | RealmScore |
| Package / applicationId | `de.morzo.realmscore` |
| versionName | `1.0.0` |
| versionCode | `1` |
| Lizenz | GPL-3.0-or-later |
| Store | nur F-Droid |
| SourceCode / Repo | GitHub (URL wird vom User gesetzt, Platzhalter verwenden) |
| Markenname | "Fantasy Realms" nur in Beschreibung, nicht als App-Name |

---

## Scope dieser Phase

### Drin
1. Package-Rename `de.basti.*` → `de.morzo.realmscore` (vollstaendig, inkl. Ordnerstruktur)
2. App-Name in `strings.xml` auf "RealmScore"
3. `versionCode` / `versionName` setzen
4. GPL-3.0 LICENSE-Datei + Lizenz-Hinweise
5. Release-Build-Config (`minifyEnabled`, `shrinkResources`, ProGuard-Regeln)
6. ProGuard-Keep-Regeln fuer Compose, Room, kotlinx.serialization
7. AndroidManifest aufraeumen (Permissions pruefen, `android:exported`)
8. `.gitignore` absichern (keine Keystores, keine `local.properties`)
9. Fastlane-Metadaten-Struktur fuer F-Droid
10. README.md
11. F-Droid-Konformitaets-Check
12. App-Icon-Slot vorbereiten (User liefert KI-generiertes Icon, hier nur Struktur + Hinweis)

### Explizit NICHT drin
- Kein eigener Keystore / keine echte Signing-Config (F-Droid signiert)
- Keine neuen Features
- Kein Play-Store-spezifisches Zeug (kein AAB-Upload, keine Play-Console-Metadaten)

---

## Aufgaben im Detail

### 1. Package-Rename `de.basti.*` → `de.morzo.realmscore`

Dies ist die heikelste Aufgabe. Vollstaendig und konsistent durchfuehren:

- **`applicationId`** in `app/build.gradle.kts` auf `de.morzo.realmscore`
- **`namespace`** in `app/build.gradle.kts` auf `de.morzo.realmscore`
- **Alle `package`-Deklarationen** in `.kt`-Dateien
- **Alle `import de.basti.*`-Statements**
- **Ordnerstruktur** physisch verschieben: `app/src/main/java/de/basti/...` → `app/src/main/java/de/morzo/realmscore/...`
- Gleiches fuer `app/src/test/...` und `app/src/androidTest/...`
- **AndroidManifest.xml**: falls vollqualifizierte Namen vorkommen
- **Room-Schema-Pfade**: falls `app/schemas/de.basti.*` existiert, regenerieren oder umbenennen

Pruefen mit:
```bash
grep -rn "de.basti" app/src/
```
Nach dem Rename darf dieser Befehl **nichts** mehr liefern.

> Tipp: Falls die alte Struktur Unterpackages hatte (z.B. `de.basti.toolchaintest` und `de.basti.fantasyrealms`), alles unter dem **einen** neuen Namespace `de.morzo.realmscore` zusammenfuehren.

### 2. App-Name

In `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">RealmScore</string>
```

Falls es weitere Sprach-Verzeichnisse gibt (z.B. `values-en/`): dort ebenfalls "RealmScore" (Eigenname, wird nicht uebersetzt).

### 3. Version

In `app/build.gradle.kts`:
```kotlin
defaultConfig {
    applicationId = "de.morzo.realmscore"
    minSdk = 26
    targetSdk = 34   // oder hoeher, aktuell
    versionCode = 1
    versionName = "1.0.0"
}
```

### 4. GPL-3.0 Lizenz

- **`LICENSE`** im Repo-Root: vollstaendiger GPL-3.0-Text (von https://www.gnu.org/licenses/gpl-3.0.txt)
- **Lizenz-Header** optional in jede `.kt`-Datei (F-Droid verlangt das nicht zwingend, aber sauber). Falls gemacht, kurzer Header:
  ```kotlin
  /*
   * RealmScore - Punktezaehler fuer das Kartenspiel Fantasy Realms
   * Copyright (C) 2026 <Name/Handle>
   * Lizenziert unter GPL-3.0-or-later. Siehe LICENSE.
   */
  ```
- In `app/build.gradle.kts` oder einer `about`-Sektion sicherstellen, dass die Lizenz erwaehnt wird (war Teil von Phase 15)

### 5. Release-Build-Konfiguration

In `app/build.gradle.kts`:
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        // KEINE signingConfig hier - F-Droid signiert selbst
    }
    debug {
        applicationIdSuffix = ".debug"
        isMinifyEnabled = false
    }
}
```

### 6. ProGuard-Regeln

`app/proguard-rules.pro` mit Keep-Regeln:
```proguard
# Jetpack Compose
-keep class androidx.compose.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class de.morzo.realmscore.**$$serializer { *; }
-keepclassmembers class de.morzo.realmscore.** {
    *** Companion;
}

# Domain-Modelle, die via Serialization geladen werden (Karten-JSON)
-keep class de.morzo.realmscore.domain.model.** { *; }
-keep class de.morzo.realmscore.data.cards.** { *; }
```

Nach Konfiguration testen:
```bash
./gradlew assembleRelease
```
Muss fehlerfrei durchlaufen. Danach die Release-APK manuell auf dem Geraet/Emulator testen (komplett durchklicken!), da Minify manchmal Reflection-basierte Sachen bricht.

### 7. AndroidManifest aufraeumen

- `android:label="@string/app_name"`
- Pruefen, dass **keine** unnoetigen Permissions drin sind (im MVP ohne Kamera: eigentlich gar keine gefaehrlichen Permissions noetig)
- `android:exported` auf der MainActivity korrekt gesetzt (`true` fuer Launcher-Activity)
- `android:allowBackup` bewusst setzen (empfohlen: `true`, da reine lokale Daten)
- Keine `<uses-permission>` fuer INTERNET, falls die App wirklich offline ist (F-Droid mag minimale Permissions; wenn INTERNET nicht gebraucht wird, raus damit)

### 8. .gitignore absichern

Sicherstellen, dass folgende Eintraege vorhanden sind:
```gitignore
*.keystore
*.jks
local.properties
/.gradle
/build
/app/build
/captures
.idea/
*.iml
.DS_Store
```

**Kritisch:** keine Signing-Keys, keine `local.properties` (enthaelt SDK-Pfade) ins Repo.

### 9. Fastlane-Metadaten-Struktur (fuer F-Droid)

F-Droid liest App-Metadaten aus dem Repo, wenn sie in der Fastlane-Struktur liegen. Anlegen:

```
fastlane/metadata/android/
├── de-DE/
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── changelogs/
│   │   └── 1.txt
│   └── images/
│       ├── icon.png                  (512x512, vom User geliefert)
│       └── phoneScreenshots/
│           ├── 1.png
│           ├── 2.png
│           └── 3.png
└── en-US/
    ├── title.txt
    ├── short_description.txt
    ├── full_description.txt
    └── changelogs/
        └── 1.txt
```

**Inhalte:**

`de-DE/title.txt`:
```
RealmScore
```

`de-DE/short_description.txt` (max ~80 Zeichen):
```
Punktezähler für das Kartenspiel Fantasy Realms
```

`de-DE/full_description.txt`:
```
RealmScore ist ein einfacher, schneller Punktezähler für das Kartenspiel Fantasy Realms (im deutschen Handel als "Fantastische Reiche" erschienen).

Funktionen:
- Punkte für 2 bis 6 Spieler erfassen
- Automatische Berechnung aller Boni, Strafen und Joker-Effekte
- Spannungs-Modus: Punkte werden erst am Rundenende gemeinsam aufgelöst
- Sandbox zum freien Ausprobieren von Kartenkombinationen
- Ausführliche Statistiken über Spieler und Karten
- Komplett offline, keine Werbung, keine Tracker

RealmScore ist eine inoffizielle Fan-App und steht in keiner Verbindung zu den Herausgebern des Spiels.
```

`de-DE/changelogs/1.txt`:
```
Erste Veröffentlichung.
- Punkteerfassung für 2-6 Spieler
- Sandbox-Modus
- Statistiken
```

`en-US/*` analog auf Englisch (Eigenname "RealmScore" bleibt; Beschreibung uebersetzen).

> **Disclaimer in der Beschreibung ist wichtig** (Markenrecht): explizit "inoffizielle Fan-App, keine Verbindung zu den Herausgebern".

### 10. README.md

Im Repo-Root:
```markdown
# RealmScore

Ein offline Punktezähler für das Kartenspiel *Fantasy Realms* (dt. *Fantastische Reiche*).

> Inoffizielle Fan-App. Steht in keiner Verbindung zu den Herausgebern des Spiels.

## Features
- Punkteerfassung für 2-6 Spieler
- Automatische Bonus-/Strafen-/Joker-Berechnung
- Sandbox zum Experimentieren
- Statistiken
- Komplett offline, werbefrei, keine Tracker

## Build
\`\`\`bash
./gradlew assembleDebug
\`\`\`

## Lizenz
GPL-3.0-or-later. Siehe [LICENSE](LICENSE).

## Tech
Kotlin, Jetpack Compose, Room, DataStore. F-Droid-konform.
```

### 11. F-Droid-Konformitaets-Check

Abschliessend ausfuehren:
```bash
./gradlew app:dependencies | grep -E "(com.google.android.gms|firebase|mlkit|com.google.android.play)"
```
Muss **leer** sein. Falls etwas auftaucht: Dependency identifizieren und entfernen/ersetzen.

Zusaetzlich pruefen, dass keine `google-services`-Plugins in den Gradle-Files stehen.

### 12. App-Icon-Slot

Der User liefert ein KI-generiertes Icon selbst. Diese Phase bereitet nur die Struktur vor:
- Sicherstellen, dass `res/mipmap-anydpi-v26/ic_launcher.xml` (Adaptive Icon) existiert
- Platzhalter-Vektoren in `res/drawable/` so benennen, dass der User sein Icon leicht einsetzen kann
- Kurzer Kommentar/Hinweis im Code oder README, welche Dateien das Icon ausmachen und welche Groessen gebraucht werden (mdpi bis xxxhdpi, plus 512x512 fuer F-Droid-Metadaten)
- **Nicht** selbst ein finales Icon erfinden – nur die Slots sauber vorbereiten

---

## Akzeptanzkriterien

- [ ] `grep -rn "de.basti" app/src/` liefert nichts
- [ ] `applicationId` und `namespace` sind `de.morzo.realmscore`
- [ ] App-Name ist "RealmScore" (alle Sprach-Verzeichnisse)
- [ ] versionCode = 1, versionName = "1.0.0"
- [ ] `LICENSE` (GPL-3.0) im Repo-Root vorhanden
- [ ] `./gradlew assembleDebug` laeuft fehlerfrei
- [ ] `./gradlew assembleRelease` laeuft fehlerfrei (mit Minify + ProGuard)
- [ ] Release-APK wurde manuell durchgeklickt und funktioniert (kein Minify-Crash)
- [ ] AndroidManifest enthaelt keine unnoetigen Permissions
- [ ] `.gitignore` schliesst Keystores + local.properties aus
- [ ] Fastlane-Metadaten-Struktur (de-DE + en-US) vollstaendig angelegt
- [ ] Beschreibung enthaelt den "inoffizielle Fan-App"-Disclaimer
- [ ] README.md vorhanden
- [ ] F-Droid-Konformitaets-Check ist leer (keine Google-Libs)
- [ ] App-Icon-Slots sind vorbereitet (User setzt sein Icon ein)

---

## Hinweise

- **Package-Rename in Android Studio**: Falls Claude Code unsicher ist, kann der Rename auch teilweise ueber Android Studios "Refactor > Rename" gemacht werden. Wichtig ist nur das Endergebnis: alles unter `de.morzo.realmscore`.
- **Release-Test ist Pflicht**: Minify/R8 entfernt ungenutzten Code und kann Reflection-basierte Dinge (Room, Serialization) brechen. Die Release-APK **muss** einmal komplett durchgeklickt werden.
- **Kein Keystore**: Bewusst keine Signing-Config mit echten Keys. F-Droid baut reproduzierbar und signiert selbst.
- **Screenshots** fuer die Metadaten macht der User manuell (Emulator/Geraet). Die `images/phoneScreenshots/`-Dateien sind erstmal Platzhalter bzw. werden vom User eingefuegt.
- **GitHub-URL**: ueberall wo die Repo-URL gebraucht wird (README, ggf. About-Screen) einen klar markierten Platzhalter setzen, den der User ersetzt: `https://github.com/<USER>/realmscore`.
