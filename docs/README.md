# Fantasy Realms Scoring App — Dokumentation (Ist-Stand)

> **Zweck dieses Ordners:** Beschreibt den **tatsächlichen, aktuellen Stand** des Codes
> (Stand 2026-06, App-Version **1.1.0**). Die `specs/` beschreiben die *geplante* Umsetzung
> pro Phase und weichen an vielen Stellen vom umgesetzten Code ab — diese Doku ist die
> verlässliche Referenz. Bei Konflikt zwischen `specs/` und diesem Ordner gilt **dieser Ordner**
> (bzw. der Code selbst).

Eine Android-App zum Punkte-Erfassen für das Kartenspiel **Fantastische Reiche** (Fantasy
Realms, Bruce Glassco / WizKids). Komplett offline, F-Droid-konform, datenschutzfreundlich.

## Dokumente

| Datei | Inhalt |
|---|---|
| [`architecture.md`](architecture.md) | Layer-Trennung, manuelle DI (`AppContainer`), Repositories, App-Start, Lokalisierung |
| [`data-model.md`](data-model.md) | Room-Entities, DAOs, „State-aus-Historie"-Prinzip, Migrationen, DB-Version |
| [`scoring-engine.md`](scoring-engine.md) | Scoring-Pipeline, 53 Karten, Joker, Blanking, Strafen-Cancellation, OptimalSolver, Ring-Layout |
| [`navigation-and-screens.md`](navigation-and-screens.md) | Alle Routes, Screens, Tabs und Klick-Flows |
| [`build-and-tooling.md`](build-and-tooling.md) | Build-Kommandos, Tech-Stack-Versionen, F-Droid-Check, Tests |
| [`phase-history.md`](phase-history.md) | Phasen-Mapping `specs/` → Ist-Zustand inkl. bekannter Abweichungen |

## Eckdaten (Ist-Stand)

- **Package / applicationId:** `de.morzo.realmscore` (Debug-Suffix `.debug`)
- **versionName / versionCode:** `1.1.0` / `2`
- **minSdk / targetSdk / compileSdk:** 29 / 36 / 36
  (minSdk wurde von 26 auf **29** angehoben — für automatische Silbentrennung `Hyphens.Auto`)
- **Java / Kotlin Target:** 17
- **Room DB-Version:** **7** (eine echte Migration `6→7`, sonst destruktiver Fallback — siehe `data-model.md`)
- **Sprachen:** Deutsch (Default) + Englisch, plus „System folgen" (Auswahl im Onboarding & Settings)
- **DI:** manuell über `AppContainer` (kein Hilt)

## Funktionsumfang (umgesetzt)

- Onboarding mit Sprachauswahl, Owner-Profil
- Bottom-Nav mit 5 Tabs: **Home · Historie · Statistiken · Sandbox · Einstellungen**
- Neues Spiel (2–6 Spieler, Modus *feste Rundenzahl* oder *Punktelimit*)
- Zweistufiger **Erfassungs-Flow** pro Runde (KartenPick ↔ Spieler-Stage), optional Mittelfeld-Erfassung
- **Reveal** (Spannungsmodus) + **Runden-Zusammenfassung** + **Spielende**
- Vollständige **Scoring-Engine** (alle 53 Grundspiel-Karten, alle Joker, Blanking, Strafen-Aufhebung)
- **Ring-Diagramm** zur Bonus-/Blanking-Visualisierung einer Hand
- **Sandbox** (Einzel-Hand, Favoriten, Multi-Hand, „Optimal lösen")
- **Statistiken** (Spieler, Karten, Head-to-Head, Zufalls-Stat auf Home)
- **Profilverwaltung**, Theme (hell/dunkel/system + dynamische Farben), **Backup-Export (JSON)**, App-Reset

## Noch nicht umgesetzt (Phase 2 / Zukunft)

Diese `specs/` sind als zukünftige Arbeit vorhanden, aber **nicht** implementiert:

- **Phase 26** — Kamera-Scan + Tesseract OCR (`specs/26-Kamera-Tesseract.md`)
- **Phase 28** — P2P-Sync via NFC/QR + Bluetooth (`specs/28-P2P-Sync.md`)
- **Phase 30** — Erweiterung „Der verfluchte Schatz" (+47 Karten) (`specs/30-Erweiterung-Verfluchter-Schatz.md`)

> Hinweis: Die Spec-Nummerierung hat Lücken/Umbenennungen (kein 21/27/29; 26/28/30 wurden
> zuletzt umnummeriert). Die obigen drei sind die einzigen offenen Zukunfts-Specs.
