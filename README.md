# RealmScore

Ein offline Punktezähler für das Kartenspiel *Fantasy Realms* (dt. *Fantastische Reiche*).

> Inoffizielle Fan-App. Steht in keiner Verbindung zu den Herausgebern des Spiels.

## Features

- Punkteerfassung für 2–6 Spieler
- Automatische Bonus-/Strafen-/Joker-Berechnung
- Spannungs-Reveal: alle Hände werden gemeinsam am Rundenende aufgelöst
- Sandbox zum Experimentieren mit Kartenkombinationen
- Ausführliche Statistiken über Spieler und Karten
- Komplett offline, werbefrei, keine Tracker

## Build

Voraussetzungen: JDK 21 (z. B. JBR aus Android Studio), Android SDK 36 (compileSdk).

```bash
./gradlew assembleDebug
```

Die APK landet unter `app/build/outputs/apk/debug/`.

F-Droid-Konformitäts-Check (muss leer sein):

```bash
./gradlew :app:dependencies | grep -E "(gms|firebase|mlkit|google-services)"
```

## Lizenz

GPL-3.0-or-later. Siehe [LICENSE](LICENSE).

## Tech

Kotlin, Jetpack Compose, Room, DataStore. F-Droid-konform.

## Quellcode

<https://github.com/<USER>/realmscore>  <!-- Platzhalter: vom Maintainer ersetzen -->
