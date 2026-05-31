# 00 – Vision & Übergreifender Kontext

Diese Datei wird von **jeder** Phasen-Datei referenziert. Sie enthaelt die uebergreifenden Constraints, Architektur-Prinzipien und Tech-Stack-Festlegungen, die in jeder Phase gelten.

---

## Was die App ist

Eine Android-App zum Punkte-Erfassen fuer das Kartenspiel **Fantastische Reiche** (Strohmann/WizKids, original "Fantasy Realms" von Bruce Glassco).

**Kern-Wertversprechen:**
- Schnelles, fehlerarmes Punkte-Erfassen am Tisch
- Reichhaltige Statistiken ueber Zeit
- Sandbox zum Experimentieren mit Karten-Kombinationen
- Komplett offline, datenschutzfreundlich

**Zielgruppe:** Spielgruppen, die regelmaessig Fantastische Reiche spielen und Wert auf eine schnelle, schoene Score-Erfassung legen.

---

## Plattform & Distribution

- **Android nativ** (Min SDK 26, Target SDK 34+)
- **Primaerer Form-Factor:** Smartphone, Portrait
- Tablets funktional unterstuetzt, kein dediziertes Layout im MVP
- **F-Droid-konform** (harte Anforderung):
  - keine Google Play Services
  - kein Firebase
  - **kein ML Kit** (nicht F-Droid-tauglich)
  - kein proprietaeres Tracking/Analytics
- Komplett offline, keine Cloud, keine Sync (P2P-Sync ist Phase 2)

---

## Tech-Stack (verbindlich)

- **Kotlin** 2.0+ mit Compose Compiler Plugin
- **Jetpack Compose** + **Material 3**
- **AndroidX Navigation Compose**
- **Room** (`runtime`, `ktx`, `compiler` via KSP)
- **DataStore Preferences**
- **kotlinx.coroutines**, **kotlinx.serialization** (fuer Karten-JSON)
- **Gradle Kotlin DSL** + Version Catalog (`libs.versions.toml`)
- **Manuelle DI** ueber `AppContainer` (kein Hilt)

**Spaetere Phasen (Phase 2 nach Release):**
- **CameraX** + **tesseract4android** fuer OCR-basierte Karten-Erkennung

---

## Architektur-Prinzipien

### Layer-Trennung
- **`domain/`**: pure Kotlin, keine Android-Imports
- **`data/`**: Room, DataStore, Repositories
- **`ui/`**: Compose, ViewModels, Navigation

### DI
- `AppContainer` als einfache Klasse mit lazy Properties
- ViewModels via `ViewModelProvider.Factory` mit Container-Referenz

### State aus Historie
- Der **Zustand eines Spiels** (Punkte, Sieger, ob vorbei) wird **nicht persistiert**, sondern aus den Runden-/Karten-Tabellen berechnet
- `Game.closedAt` ist die einzige State-Variable (offen / geschlossen)
- Vorteil: Stats und Live-Stand nutzen dieselbe Datenquelle, kein Drift moeglich

### Sync-Vorbereitung
- **UUIDs als Primary Keys** (String, kein Auto-Increment)
- **`originDeviceId`** auf allen schreibenden Entitaeten
- **`createdAt` + `updatedAt`** ueberall (epoch millis)
- **Profile-ID deterministisch:** `sha256(deviceUuid + "|" + name.lowercase()).take(32)`
- **`isLocalOwner`** auf Profile (genau einer pro Device)

### i18n
- **Alle** Texte aus `strings.xml` ab Phase 01
- Default-Sprache Deutsch, englische Uebersetzung ist Phase 2
- Scoring-Engine liefert **String-Resource-Keys**, keine fertigen Texte

---

## Gameplay-Regeln (kompakt)

- **2–6 Spieler**, 7 Karten pro Hand
- **Spielmodi:** Feste Rundenanzahl ODER Punktelimit (Romme-artig)
- **Joker-Karten:** Doppelgaenger (nur eigene Hand), Spiegelung & Gestaltenwandler (alle 53), Buch der Wandlungen (Suit-Wechsel)
- **Mittelfeld-Karten** (Discard-Pile) sind optional erfassbar – noetig fuer Totenbeschwoerer und Karten-Statistiken
- **Scoring-Reihenfolge:** Joker → Buch der Wandlungen → Strafen aufheben → Daemon-Blanking → Strafen anwenden → Score

---

## MVP-Entscheidungen (Stand der Kompromisse)

| Thema | Entscheidung |
|-------|--------------|
| Plattform | Android, F-Droid |
| Spielerzahl | 2–6 |
| Karten-Erfassung | **MVP: manuell ueber Picker; Phase 2: Tesseract OCR** |
| Karten-Umfang | Grundspiel (53 Karten); Erweiterung "Der verfluchte Schatz" ist Phase 2 |
| Spielmodi | Feste Rundenanzahl ODER Punktelimit |
| Spielende | User-getriggert, nicht automatisch |
| Pause/Resume | Implizit: mehrere offene Spiele parallel moeglich, einfach App verlassen |
| Korrektur | Nur die letzte abgeschlossene Runde, bis neue Runde startet |
| Profile | Name + Farbe (auto), Owner via Onboarding, andere implizit beim Spielanlegen |
| Reveal-Modus | **Spannungs-Modus ist einziger MVP-Modus** (alle warten, dann gemeinsamer Reveal) |
| Sandbox | Vollwertiges Feature; spaeter "Move to Sandbox" aus Historie heraus |
| Statistik-Fokus | Kern-Wertversprechen; Spieler- und Karten-Stats |
| Sprache | Deutsch zuerst, i18n-ready |
| Backup/Export | Phase 2 |
| Online-Multiplayer | Nein |
| P2P-Sync | Phase 2 (NFC + Bluetooth/WiFi-Direct) |

---

## Klick-durch-Prinzip

Jede Phase dieser App liefert einen Klick-Pfad **vom Start bis zum aktuellen Feature-Ende**. Neue Screens, die noch nicht implementiert sind, sind als **Platzhalter** ("X kommt hier hin") vorhanden, damit die App immer kohaerent durchklickbar ist.

Beispiel nach Phase 01: Onboarding → Platzhalter "Hauptmenu kommt hier hin".

---

## Datenmodell-Skizze (entsteht inkrementell)

Wird in den jeweiligen Phasen aufgebaut. Hier nur die Grobstruktur:

```
Profile        (id, name, color, isLocalOwner, ...)
Game           (id, displayName?, mode, target, closedAt?, closedReason?, ...)
GameParticipant(gameId, profileId, seatOrder, lastScanOrder?)
Round          (id, gameId, roundNumber, completedAt?, discardScanned, ...)
RoundResult    (id, roundId, profileId, totalScore, ...)
HandCard       (id, roundResultId, cardKey, position, jokerTargetCardKey?, ...)
DiscardCard    (id, roundId, cardKey)
```

---

## F-Droid-Konformitaets-Check

Vor jedem Commit pruefen:
```bash
./gradlew app:dependencies | grep -E "(gms|firebase|mlkit|google-services)"
```
Dieses Kommando darf **nichts** zurueckliefern.

---

## Was diese Datei NICHT ist

Diese Datei beschreibt **was die App ist und welche Constraints gelten**. Die **konkrete Umsetzungs-Reihenfolge** steht in den nummerierten Phasen-Dateien (`01-Onboarding.md` bis `15-Polish.md`).

Jede Phase ist so geschnitten, dass sie einen abgeschlossenen, testbaren Stand liefert.
