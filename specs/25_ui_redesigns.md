# Phase 25 — UI-Redesigns (Übersicht / Index)

Diese Phase bündelt mehrere voneinander unabhängige Umsetzungsprojekte. Jedes
Projekt liegt jetzt in **einer eigenen Datei** und ist eigenständig umsetzbar. Die
Dateien sind in **Umsetzungsreihenfolge** durchnummeriert (`25.1` zuerst … `25.6`
zuletzt) — eine Dateiliste sortiert damit genau in der Implementierungsreihenfolge.

Vor Beginn eines Projekts: `00-vision.md` + die jeweilige Projektdatei lesen, dann
das Projekt vollständig umsetzen.

Stand der Codebasis bei Erstellung: Branch `V1.1.0`, MVP (01–17) + Phasen 18–22
umgesetzt. Relevante Ist-Stände sind je Projekt unter „Ist-Zustand" notiert.

## Umsetzungsreihenfolge

| #   | Datei                                                              | Titel                                                          | Kern-Dateien | Abhängig von |
| --- | ------------------------------------------------------------------ | -------------------------------------------------------------- | ------------ | ------------ |
| 1   | [`25.1-Sprachauswahl-Onboarding.md`](25.1-Sprachauswahl-Onboarding.md) | Sprachauswahl im Onboarding (Flaggen-Buttons)            | `OnboardingScreen.kt`, `AppLanguage` | — |
| 2   | [`25.2-Tastatur-IME-Insets.md`](25.2-Tastatur-IME-Insets.md)       | Tastatur verdeckt Eingabefelder/Buttons (IME-Insets)          | `AndroidManifest.xml`, `OnboardingScreen.kt`, `NewGameScreen.kt` | — |
| 3   | [`25.3-Ring-Diagramm-Redesign.md`](25.3-Ring-Diagramm-Redesign.md) | Ring-Diagramm Redesign (Blanking, Kurven, Gradient)           | `HandRingView.kt`, `RingLayoutOptimizer.kt` | — |
| 4   | [`25.4-Joker-Auswertung.md`](25.4-Joker-Auswertung.md)             | Joker-Auswertung nach Berechnungsreihenfolge + Totenbeschwörer | `JokerResolver.kt`, `ScoringEngine.kt`, `OptimalSolver.kt` | — |
| 5   | [`25.5-Erfassungs-Flow.md`](25.5-Erfassungs-Flow.md)               | Erfassungs-Flow als ein Konstrukt (KartenPick ↔ Spieler-Stage) | `RoundCaptureScreen.kt`, `PlayerHandCaptureContent.kt`, `CardPicker.kt` | 25.4 |
| 6   | [`25.6-Sandbox-UI-Rework.md`](25.6-Sandbox-UI-Rework.md)           | Sandbox-UI-Rework (Einzel-Hand, Favoriten-Liste, Multi-Hand)  | `SandboxScreen.kt`, `ScoreFooter.kt`, `SandboxFavoritesScreen.kt`, `MultiHandScreen.kt` | 25.3, 25.4, 25.5 |

## Mapping alte → neue Nummer

Die Projekte wurden in Umsetzungsreihenfolge **neu nummeriert**. Querverweise in den
Projektdateien nutzen bereits die **neuen** Nummern.

| Neu (Reihenfolge) | Alt (gebündelte Datei) | Titel |
| ----------------- | ---------------------- | ----- |
| 25.1 | 25.5 | Sprachauswahl im Onboarding |
| 25.2 | 25.4 | Tastatur / IME-Insets |
| 25.3 | 25.1 | Ring-Diagramm Redesign |
| 25.4 | 25.3 | Joker-Auswertung + Totenbeschwörer |
| 25.5 | 25.2 | Erfassungs-Flow |
| 25.6 | 25.6 | Sandbox-UI-Rework |
