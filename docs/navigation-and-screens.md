# Navigation & Screens

Zwei NavHosts (siehe `architecture.md`): der äußere (`ui/nav/AppNavHost.kt`) für
Splash/Onboarding/Main, der innere (`ui/nav/MainScaffold.kt`) für alle Tabs und Detail-Screens.
Alle Routen-Konstanten in `ui/nav/Routes.kt`.

## Bottom-Navigation (5 Tabs)

Definiert in `MainScaffold` (`AppBottomNavigation`):

| Tab | Route | Icon | Screen |
|---|---|---|---|
| Home | `tab_home` | Home | `HomeScreen` |
| Historie | `tab_history` | DateRange | `HistoryScreen` |
| Statistiken | `tab_stats` | List | `StatsOverviewScreen` |
| Sandbox | `sandbox` | Casino | `SandboxScreen` |
| Einstellungen | `tab_settings` | Settings | `SettingsScreen` |

> Inset-Hinweis: Das äußere Scaffold besitzt nur den Bottom-Nav-Inset. Den Status-Bar-Inset (oben)
> handhabt jeder Screen selbst (TopAppBar-Screens zeichnen darunter; nackte Tab-Screens fügen ihn
> wieder hinzu). `consumeWindowInsets` verhindert doppelte Bottom-Insets.

## Spiel-Flow (Hauptpfad)

```
Home ──"Neues Spiel"──▶ NewGameScreen ──▶ GameInProgressScreen (game/{gameId})
                                                  │ onStartRound(roundId)
                                                  ▼
                                          RoundCaptureScreen (round/{roundId}/capture)   ← zweistufiger Erfassungs-Flow (spec 25.5)
                                                  │ onAllPlayersCaptured
                                                  ▼
                                          RevealScreen (round/{roundId}/reveal)          ← Spannungs-Reveal
                                                  │ onDone / onSkip
                                                  ▼
                                          RoundSummaryScreen (round/{roundId}/summary)
                                          ├─ onNextRound ──▶ nächste RoundCapture
                                          ├─ onEditRound ──▶ RoundCapture (gleiche Runde, letzte Runde korrigierbar)
                                          ├─ onShowRevealAgain ──▶ Reveal
                                          └─ "Spiel abschließen" (vm.completeGame: schließt das Spiel JETZT
                                             + p2p.closeSharedGame) ──▶ GameSummaryScreen (game/{gameId}/summary, bereits geschlossen)
                                                                  ├─ onShowStats ──▶ Stats-Tab
                                                                  ├─ Host/Solo: "Neues Spiel starten" (onNewGame, seedGameId+continueSession) ──▶ NewGame (vorbefüllt)
                                                                  └─ Alle Geräte: "Zurück zum Hauptmenü" (vm.leaveToMenu → p2p.close() + onCloseGameDone) ──▶ Home (frischer Zustand, keine Verbindung)
```

> **Game-End-Flow (geändert):** Das Spiel wird schon mit dem **ersten** Button ("Spiel abschließen" in
> RoundSummary) als geschlossen gespeichert; im P2P holt `closeSharedGame` die beigetretenen Telefone
> jetzt schon auf den Game-End-Screen. Der zweite Button ist "Neues Spiel starten" (Host/Solo, mit
> vorbefüllten Spielern + Einstellungen via `Routes.newGameRoute(seedGameId, continueSession)`); auf
> dem Host bringt er die anderen Telefone mit (`SyncMessage.NewGameSetup` → `NavSignal.OpenNewGameWait`
> → `NewGameWaitScreen`, dann zieht das übliche `OpenRound` alle in die Erfassung). **Jedes Gerät**
> (Host, Joined-Phone, Solo) hat zusätzlich "Zurück zum Hauptmenü" (`vm.leaveToMenu`): das reißt eine
> laufende P2P-Session ab (`p2p.close()` → alle Sockets zu, `SessionState.Idle`) und navigiert via
> `onCloseGameDone` nach Home + leert den Game-Back-Stack — der Nutzer landet in einem frischen,
> verbindungsfreien Zustand. Auf dem Host steht der Button als sekundäre `OutlinedButton` unter "Neues
> Spiel starten", auf dem Joined-Phone ist er die einzige Aktion. Der frühere zweistufige
> Schließen-Flow / "Zurück zum Spiel" entfällt im Abschluss-Pfad.

**RoundCaptureScreen** (`ui/game/RoundCaptureScreen.kt`, VM `RoundCaptureViewModel`) ist der aktuelle
zweistufige Erfassungs-Flow (KartenPick-Stage ↔ Spieler-Stage, spec 25.5). Die Hand-Erfassung pro
Spieler nutzt `ui/handentry/` (`PlayerHandCaptureContent`, `OverlappingHandStack`,
`PlayerHandEntryScreen`/`PlayerHandEntryViewModel`). Optionales Mittelfeld wird in derselben
Rotation als „Pseudo-Spieler" erfasst (gated durch `discardCaptureEnabled`).

> Legacy: Route `round/{roundId}` (`RoundEntryScreen`/`RoundEntryViewModel`) ist noch registriert,
> der Hauptpfad ab Phase 25.5 läuft aber über `…/capture`. Beim Dokumentieren neuer Features den
> Capture-Flow als Referenz nehmen.

## Sandbox

- `sandbox?launchType=…&gameId=…&roundId=…&profileId=…&favoriteId=…` — ein parametrisierter Screen
  mit drei Launch-Modi (`SandboxLaunchData`): `Empty`, `FromRound` (Move-to-Sandbox), `FromFavorite`.
- `SandboxScreen` (`ui/sandbox/`) — Einzel-Hand-Editor mit Karten-Slots (`CardSlot`,
  `HandSlotsRow`), Joker-Sektion (`JokerSection`), Score-Breakdown-Sheet, „Optimal lösen".
- `sandbox/favorites` — `SandboxFavoritesScreen` (gespeicherte Hände laden/verwalten).
- `MultiHandScreen` (`ui/sandbox/multihand/`) — mehrere Hände vergleichen.

**Move-to-Sandbox**: aus GameInProgress, RoundSummary, GameSummary, PlayerStats und CardStats heraus
via `sandboxRouteFromRound(gameId, roundId, profileId)` aufrufbar
(`ui/sandbox/components/MoveToSandboxIcon.kt`).

## Statistiken

- `tab_stats` → `StatsOverviewScreen`
- `stats/player/{profileId}` → `PlayerStatsScreen` (→ Head-to-Head)
- `stats/cards` → `CardStatsOverviewScreen`
- `stats/card/{cardKey}` → `CardStatsScreen`
- `stats/h2h/{a}/{b}` → `HeadToHeadScreen`

## Einstellungen & Profile

- `tab_settings` → `SettingsScreen` (Sprache, Theme, dynamische Farben, Default-Rundenzahl/-Limit,
  Mittelfeld-Erfassung, Picker-Suche, **Backup-Export**, **App-Reset**).
- `settings/username_change` → `UsernameChangeScreen`
- `settings/profiles` → `ProfileManagementScreen`
- **App-Reset** startet `MainActivity` mit `CLEAR_TASK` neu (Splash → kein Owner → Onboarding).

## Vollständige Routen-Tabelle

Siehe `ui/nav/Routes.kt` als Quelle der Wahrheit. Builder-Funktionen (`gameRoute`,
`roundCaptureRoute`, `sandboxRouteFromRound`, …) kapseln das Pfad-Format.
