# Phasen-Historie: `specs/` → Ist-Zustand

Diese Tabelle ordnet die Spec-Phasen dem **tatsächlich umgesetzten** Code zu. Die `specs/` sind die
*ursprüngliche Planung*; wo die Umsetzung abwich und die Spec nicht nachgezogen wurde, steht es unter
„Abweichungen". Alle Phasen bis **25.7** sind umgesetzt.

## Status-Übersicht

| Phase | Thema | Status |
|---|---|---|
| 00 | Vision & Kontext | Referenz |
| 01 | Onboarding | ✅ (erweitert um Sprachauswahl, 25.1) |
| 02 | Hauptmenü / Bottom-Nav | ✅ |
| 03 | Sandbox mit Dummy-Karten | ✅ (in spätere Sandbox aufgegangen) |
| 04 | Sandbox mit 53 echten Karten + CardPicker | ✅ |
| 05 | Sandbox mit Scoring-Engine | ✅ |
| 06 | Neues Spiel (NewGameScreen) | ✅ |
| 07 | Spiel-Übersicht + RoundEntry | ✅ (RoundEntry später durch Capture-Flow ersetzt) |
| 08 | Manuelle Karteneingabe | ✅ (zu Vollbild-Flow umgebaut, 18.1 / 25.5) |
| 09 | Reveal + RoundSummary | ✅ |
| 10 | Spielende (GameSummary) | ✅ |
| 11 | Historie-Tab | ✅ |
| 12 | Statistiken | ✅ |
| 13 | Home-Tab (Games-to-continue + Random-Stat) | ✅ |
| 14 | Move-to-Sandbox | ✅ |
| 15 | Settings + Theme + Polish | ✅ |
| 16 | Release-Vorbereitung (Code-Seite) | ✅ |
| 17 | Profilverwaltung | ✅ |
| 17.1 | Totenbeschwörer als Joker | ✅ (in 25.4 zu echtem `JokerType` überarbeitet) |
| 18 | Karten-Bonus-Visualisierung (Ring) | ✅ (in 25.3 neu designt) |
| 18.1 | Kleine Änderungen / UX | ✅ |
| 18.2 | Korrekturen & Bugfixes | ✅ |
| 19 | Englische Übersetzung | ✅ |
| 20 | Mittelfeld-Scan (manuell) | ✅ (als Pseudo-Spieler in der Capture-Rotation) |
| 22 | Sandbox-Erweiterungen (Favoriten + Multi-Hand) | ✅ (UI in 25.6 überarbeitet) |
| 23 | Datenexport / Backup (JSON) | ✅ |
| 24 | Code-Review-Findings | ✅ |
| 25.1 | Sprachauswahl im Onboarding | ✅ |
| 25.2 | Tastatur-/IME-Insets | ✅ |
| 25.3 | Ring-Diagramm-Redesign | ✅ |
| 25.4 | Joker-Auswertung + Totenbeschwörer als Joker | ✅ |
| 25.5 | Erfassungs-Flow (KartenPick ↔ Spieler-Stage) | ✅ |
| 25.6 | Sandbox-UI-Rework | ✅ (DB-Migration 6→7) |
| 25.7 | Sprachanwendung in Fenstern & Karten-Namen | ✅ |
| **26** | **Kamera-Scan + Tesseract OCR** | ❌ offen (Phase 2) |
| **28** | **P2P-Sync (NFC/QR + Bluetooth)** | ❌ offen (Phase 2) |
| **30** | **Erweiterung „Der verfluchte Schatz" (+47 Karten)** | ❌ offen (Phase 2) |

> Nummern 21, 27, 29 existieren nicht (übersprungen/umnummeriert). 25 selbst ist nur ein
> Index/Übersichts-Dokument (`25_ui_redesigns.md`).

## Wesentliche Abweichungen Spec ↔ Code

Diese Punkte weichen vom Wortlaut der Specs / der alten `HANDOFF.md` ab — hier gilt der Code:

1. **Package umbenannt.** Specs/HANDOFF nennen teils `de.basti.fantasyrealms`. Tatsächlich:
   **`de.morzo.realmscore`** (Debug `…​.debug`).

2. **minSdk 26 → 29.** Wegen automatischer Silbentrennung (`Hyphens.Auto`). Spec/Vision nennen noch
   „Min SDK 26".

3. **Totenbeschwörer (17.1 → 25.4).** Ursprünglich als Sonderfall/`PlayerChoices` geplant; jetzt ein
   echter `JokerType.NECROMANCER`, der durch die normale Joker-Pipeline läuft. `PlayerChoices` wurde
   gelöscht. Keine DB-Migration nötig.

4. **Mittelfeld-Erfassung (Phase 20).** Kein separater „DiscardEntryScreen" im Hauptpfad; das
   Mittelfeld wird als **Pseudo-Spieler** in der normalen Runden-Erfassungs-Rotation erfasst
   (gated durch `discardCaptureEnabled`, vor dem Reveal). Der Kommentar in `DiscardCardEntity`
   nennt noch einen DiscardEntryScreen — das beschreibt die ursprüngliche Planung.

5. **Erfassungs-Flow (25.5).** Der frühere per-Spieler `RoundEntryScreen` ist im Hauptpfad durch den
   zweistufigen `RoundCaptureScreen` (KartenPick-Stage ↔ Spieler-Stage) ersetzt. Die alte Route
   `round/{roundId}` ist noch registriert, aber nicht der aktive Pfad.

6. **Keine Spieldauer in Zusammenfassungen.** Bewusste Entscheidung: in Summary-/Stats-Screens wird
   keine „Spieldauer"/verstrichene Zeit gezeigt (Spiele pausieren über Tage). Memo
   `feedback_no_game_duration`.

7. **Ring-Diagramm neu (25.3) und Sandbox-UI neu (25.6).** Die ursprünglichen Specs 18 / 22
   beschreiben ältere Layouts.

8. **DB-Migrationsstrategie.** Bis auf `MIGRATION_6_7` läuft die DB auf destruktivem Fallback
   (`dropAllTables`). Vor dem ersten echten Release ersetzen — siehe `data-model.md`.

## Stale Dokumente (nicht mehr als Referenz nutzen)

- **`HANDOFF.md`** — nur der oberste Status-Block ist gepflegt; der Rest ist Stand Phase 01 (altes
  Package, alte Ordnerstruktur).
- **`specs/`** — Planungsstand pro Phase, nicht durchgängig nachgezogen. Diese Doku (`docs/`) ist die
  verlässliche Ist-Referenz.
