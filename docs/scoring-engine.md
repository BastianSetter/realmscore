# Scoring-Engine

Reines Kotlin unter `domain/scoring/` — keine Android-Imports. Einstieg: `ScoringEngine.score(input)`.

## Karten-Daten

- 53 Grundspiel-Karten in `assets/cards/base_game.json`, geladen von `data/cards/CardLookup.kt` zu
  `CardDefinition` (`key`, `nameDe`, `suit`, `baseStrength`, `ruleTextDe`, `isJoker`, `jokerType?`,
  optional `nameEn`/`ruleTextEn` aus `base_game_en.json`).
- Sortierung an der Quelle: nach Suit, dann alphabetisch (DE). Alle Konsumenten (Picker, Sandbox,
  Totenbeschwörer-Kandidaten …) erben diese Reihenfolge.
- **Suits** (`domain/model/Suit.kt`): `ARMY, ARTIFACT, BEAST, FLAME, FLOOD, LAND, LEADER, WEAPON,
  WEATHER, WIZARD, WILD`. `WILD` ist die Joker-Farbe.

## Joker & Spielerwahlen (`JokerType`)

`domain/model/JokerType.kt`. Alle Spieler-Entscheidungen laufen über **einen** Mechanismus:
`JokerAssignment(jokerKey, targetCardKey?, targetSuit?)` — persistiert auf `HandCardEntity`.

- **Substitutions-Joker** (`isSubstitution = true`, eigene Stärke 0): `DOPPELGANGER` (nur eigene
  Hand), `MIRAGE` (Spiegelung, kopiert aus 5 Suits), `SHAPESHIFTER` (Gestaltenwandler, andere 5
  Suits), `BOOK_OF_CHANGES` (Buch der Wandlungen, ändert die Suit einer Zielkarte).
- **Karten mit Zielwahl** (`isJoker = false`, behalten Stärke + eigene Regel): `ISLAND` (Insel —
  hebt eine Flut-/Flammen-Strafe auf), `FOUNTAIN_OF_LIFE` (Lebensquell — kopiert eine Stärke),
  `NECROMANCER` (Totenbeschwörer — zieht eine 8. Karte aus dem Mittelfeld).

Der **Totenbeschwörer ist ein echter `JokerType.NECROMANCER`** (spec 25.4), kein Sonderfeld: er
läuft durch dieselbe Resolve-/Optimizer-/Persistenz-Pipeline. Sein Pull wird **vor** dem Buch der
Wandlungen materialisiert, sodass ein nachfolgendes Buch die 8. Karte umfärben kann.

## Pipeline (`ScoringEngine.score`)

Reihenfolge spiegelt das Regelwerk:

1. **Joker auflösen** (`JokerResolver`): Substitutionen (Name/Suit/Stärke umschreiben),
   Totenbeschwörer-Pull als zusätzliche resolved 8. Karte, Buch der Wandlungen zuletzt (kann auch
   die gezogene Karte umfärben). Alles in Berechnungsreihenfolge, jeder Joker sieht die vorherigen.
2. **Cancellations sammeln** (`PenaltyContext`): über die *volle* Hand vor Blanking. Aufhebungen
   sind Boni („hebt … auf") → nur wenn `bonusEnabled`. **Clearing ist permanent** — ein später
   geblankter Aufheber hat seine Strafe trotzdem schon aufgehoben.
3. **Blanking-Fixpunkt** (`BlankingResolver`, inkl. Selbst-Blanks): eine Karte, deren Strafe
   aufgehoben wurde, blankt nichts; geblankte Quellen verlieren ihre restlichen Effekte.
4. **Boni** je nicht-geblankter Karte (`bonusEnabled`).
5. **Strafen** je nicht-geblankter Karte (`penaltyEnabled`, `PenaltyContext` angewandt).
6. **Per-Karte-Summe** = (Basisstärke falls nicht geblankt) + Bonus-Deltas + Strafen-Deltas.

Begründung der Reihenfolge (offizielle Regel) ist als Memo
`reference_scoring_order_of_operations` festgehalten: Clearing ist permanent und geht dem Blanking
voraus; Blanking IST eine Strafe, also blankt ein geblankter Blanker nichts.

### Ein-/Ausgabe

- **`ScoringInput`**: `hand: List<CardDefinition>`, `jokerAssignments`, `discardPile`,
  `discardScanned`. Letzteres liest **nur** der `OptimalSolver` (gatet das Brute-Forcen des
  Totenbeschwörer-Picks); die Engine selbst ignoriert es.
- **`ScoringResult`**: `totalScore`, `perCard: List<CardScoreResult>`, `blankedKeys`,
  `blankedBy` (Map original→Blanker, für die Ring-Visualisierung). Jede `CardScoreResult` trägt
  `effectiveCardKey` (anzuzeigender Name), `effects`, `isBlanked`, `isNecromancerPick`,
  `bookOfChangesSuit?`.

## Regel-Registry (`rules/`)

`rules/BaseGameRules.build()` ist die **einzige Quelle der Wahrheit**: Map von 53 Karten-Keys →
`CardScoringRule`. Karten, deren einziger Beitrag ihre Basisstärke ist (z. B. Joker ohne
Zuweisung), bekommen keinen Eintrag.

Wiederverwendbare Regel-Bausteine in `rules/common/`:
- `ConditionalFlatRule` (Pauschalbonus wenn Bedingung), `FlatPenaltyIfMissingRule`,
  `PerOtherCountRule` (X pro passender anderer Karte), `SelfBlankIfRule`, `CompositeRule`.
- Matcher (`CardMatcher`): `ByKey`, `BySuit`, `AnyOf`. Bedingungen (`HandCondition`): `Contains`,
  `NotContains`, `And`, `Or`.

Sonderfälle als eigene Regel-Objekte in `rules/specials/` (z. B. `WorldTreeRule`, `UnicornRule`,
`GemOfOrderRule`, `WarlordRule`, `KingRule`/`QueenRule`, `ShieldOfKethRule`/`SwordOfKethRule`,
`CollectorRule`, `FountainOfLifeRule`, sowie Blanker/Cancel-Regeln wie `BlizzardBlankerRule`,
`RainstormBlankerRule`, `GreatFloodRule`, `CavernCancelRule`, `IslandCancelRule`,
`BeastmasterCancelRule`, `MountainCancelRule`, `WarshipCancelRule`, `RangersCancelRule`,
`BasiliskRule`, `WildfireRule`).

## OptimalSolver

`domain/scoring/solver/OptimalSolver.kt` brute-forced alle Joker-Kombinationen × Spielerwahlen
(Doppelgänger/Spiegelung/Gestaltenwandler/Buch × Insel × Lebensquell × Totenbeschwörer) und liefert
die punkt-maximale `ScoringInput`. Bei Punktgleichstand bevorzugt er die Kombination mit **mehr**
gesetzten Auswahlen (damit irrelevante Joker trotzdem einen gültigen Wert bekommen).

- Totenbeschwörer-Pick wird **nur** brute-forced, wenn `discardScanned = true` (kleine
  Kandidatenmenge); sonst wird der manuelle Pick des Nutzers durchgereicht.
- Wird in der Sandbox („Optimal lösen") und bei der Reveal-Vorberechnung genutzt.

## Ring-Layout

`domain/scoring/RingLayoutOptimizer.kt` ordnet die Karten einer Hand auf einem Kreis so an, dass
Bonus-/Blanking-Verbindungen (`RingConnection`) möglichst überschneidungsarm gezeichnet werden. Die
Compose-Darstellung liegt in `ui/components/HandRingView.kt` (+ `HandBreakdownSheet.kt`).

## Tests

Umfangreiche Unit-Tests unter `app/src/test/.../domain/scoring/`: `PerCardSmokeTest` (jede Karte),
`SpecialBonusTest`, `BlankingTest`, `JokerTest`, `NecromancerScoringTest`, `PenaltyCancellationTest`,
`OptimalSolverTest`, `RingLayoutOptimizerTest`, `EdgeCasesTest` (+ `TestFixture`). Weitere Tests für
Stats, Backup-Serialisierung, Capture-Ordering und Profile-Relevanz.
