# Phase 14 – Move-to-Sandbox

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 13 ist abgeschlossen. Home-Tab ist lebendig.

---

## Kontext (kurz)

In dieser Phase wird die **"Move to Sandbox"**-Funktionalität eingebaut: aus verschiedenen Screens (RoundSummary, GameSummary, PlayerStats, CardStats) kann der User eine bereits gespielte Hand in die Sandbox übernehmen und experimentieren ("Was wäre wenn ich Joker anders gesetzt hätte?").

Wichtig: keine "Optimal wäre X gewesen"-Hinweise – das frustriert nur. Sandbox bleibt zum freien Experimentieren.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Erweiterung des Sandbox-Screens:
  - Akzeptiert optionalen Launch-Parameter `SandboxLaunchData`
  - Vorbefüllung der Hand + Joker-Assignments aus gespeicherter Runde
  - Optional: Discard-Pile vorbefüllen
  - Origin-Banner: "Aus Spiel vom 12.10.2025, Runde 3, gespielt von Maria"
- Move-to-Sandbox-Icons in:
  - `RoundSummaryScreen` (pro Spieler-Zeile)
  - `GameSummaryScreen` (in Rundentabelle, pro Zelle)
  - `GameInProgressScreen` Spielstand-Tabelle (pro abgeschlossene-Runden-Zelle)
  - `PlayerStatsScreen` (letzte Spiele)
  - `CardStatsScreen` (höchster Einzelbeitrag-Kontext)
- Erweiterte Repository-Methoden: `getHand(roundId, profileId)` (existiert schon aus Phase 08), zusätzlich `getDiscardCards(roundId)`

### Explizit NICHT drin
- Keine "Optimal-wäre-besser-gewesen"-Vergleiche
- Kein Speichern von Sandbox-Hands (Favoriten kommen Phase 2)
- Kein Multi-Hand-Vergleich (Phase 2)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Spielen → Runde abschließen → RoundSummary
2. Auf einer Spieler-Card: Tap auf 🧪-Icon
3. Sandbox öffnet sich mit:
   - 7 Karten der Hand vorbefüllt
   - Joker-Belegungen wie gespielt
   - Origin-Banner: "Aus Runde 3 vom 12.10.2025, gespielt von Maria"
4. Score wird angezeigt (gleich wie damals)
5. User ändert eine Karte → neuer Score wird live berechnet
6. Aus PlayerStatsScreen letzte Spiele: Tap auf Sandbox-Icon → analog
7. Aus CardStatsScreen "Höchster Einzelbeitrag": Tap → Sandbox mit dieser Hand

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### SandboxLaunchData

```kotlin
// ui/sandbox/SandboxLaunchData.kt
sealed class SandboxLaunchData {
    object Empty : SandboxLaunchData()
    data class FromRound(
        val gameId: String,
        val roundId: String,
        val profileId: String
    ) : SandboxLaunchData()
}
```

### Sandbox-Route mit Parametern

```kotlin
const val SANDBOX = "sandbox?launchType={type}&gameId={gameId}&roundId={roundId}&profileId={profileId}"

fun sandboxRouteEmpty() = "sandbox"
fun sandboxRouteFromRound(gameId: String, roundId: String, profileId: String) =
    "sandbox?launchType=fromRound&gameId=$gameId&roundId=$roundId&profileId=$profileId"
```

In `MainScaffold`:
```kotlin
composable(
    route = Routes.SANDBOX,
    arguments = listOf(
        navArgument("launchType") { defaultValue = "empty"; type = NavType.StringType },
        navArgument("gameId") { defaultValue = ""; type = NavType.StringType },
        navArgument("roundId") { defaultValue = ""; type = NavType.StringType },
        navArgument("profileId") { defaultValue = ""; type = NavType.StringType }
    )
) { backStackEntry ->
    val launchData = parseLaunchData(backStackEntry)
    SandboxScreen(launchData = launchData)
}
```

### SandboxViewModel erweitern

```kotlin
class SandboxViewModel(
    private val launchData: SandboxLaunchData,
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val handCardRepo: HandCardRepository,
    private val roundRepo: RoundRepository,
    private val gameRepo: GameRepository,
    private val profileRepo: ProfileRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            when (val data = launchData) {
                is SandboxLaunchData.Empty -> {} // leer starten
                is SandboxLaunchData.FromRound -> loadFromRound(data)
            }
        }
    }

    private suspend fun loadFromRound(data: SandboxLaunchData.FromRound) {
        val savedHand = handCardRepo.getHand(data.roundId, data.profileId) ?: return
        val game = gameRepo.getById(data.gameId) ?: return
        val round = roundRepo.getRound(data.roundId) ?: return  // ggf. neue Method
        val profile = profileRepo.getById(data.profileId) ?: return
        val discardCards = roundRepo.getDiscardCards(data.roundId)
            .mapNotNull { cardLookup.getByKey(it) }

        val cards = savedHand.cards.sortedBy { it.position }.map { entry ->
            cardLookup.getByKey(entry.cardKey)!!
        }
        val slots = cards.mapIndexed { idx, card -> CardSlot.Filled(card) }.toMutableList()
        while (slots.size < 7) slots.add(CardSlot.Empty)

        val jokerAssignments = savedHand.cards
            .filter { it.jokerTargetCardKey != null }
            .associate {
                it.cardKey to JokerAssignment(
                    jokerCardKey = it.cardKey,
                    targetCardKey = it.jokerTargetCardKey!!,
                    targetSuit = it.jokerTargetSuit?.let { Suit.valueOf(it) }
                )
            }

        val originText = buildOriginText(profile, round, game)

        _uiState.update {
            it.copy(
                slots = slots,
                jokerAssignments = jokerAssignments,
                discardCards = discardCards,
                originBanner = OriginBanner(
                    text = originText,
                    gameId = data.gameId
                )
            )
        }

        recomputeScore()
    }
}

data class OriginBanner(
    val text: String,
    val gameId: String
)
```

### Move-to-Sandbox-Icons

In jedem relevanten Screen kleines Icon (Material `Icons.Default.Science` oder `Icons.Default.OpenInNew` mit Lab-Stil) das tap-bar ist:

```kotlin
@Composable
fun MoveToSandboxIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = stringResource(R.string.move_to_sandbox)
        )
    }
}
```

### Integration in Screens

**RoundSummaryScreen** (Spieler-Card):
```kotlin
@Composable
fun PlayerSummaryCard(player: PlayerSummary, ..., onMoveToSandbox: () -> Unit) {
    Card(...) {
        Row {
            // bestehende Inhalte
            Spacer(Modifier.weight(1f))
            MoveToSandboxIcon(onClick = onMoveToSandbox)
        }
    }
}
```

**GameSummaryScreen** (Rundentabelle): Move-to-Sandbox-Icon klein in jeder Zelle.

**GameInProgressScreen** (Spielstand-Tabelle): analog, aber nur für abgeschlossene Runden.

**PlayerStatsScreen** (letzte Spiele): Move-to-Sandbox-Icon pro Eintrag, fragt aber: aus welcher Runde des Spiels? → vereinfacht: nimmt die beste oder neueste Runde dieses Spielers in dem Spiel.

**CardStatsScreen** "Höchster Einzelbeitrag": Move-to-Sandbox-Icon neben dem Eintrag.

### Strings (Ergaenzungen)

```xml
<string name="move_to_sandbox">In Sandbox übernehmen</string>
<string name="sandbox_origin_banner">Aus %1$s, Runde %2$d, gespielt von %3$s</string>
<string name="sandbox_origin_no_game_name">Spiel vom %1$s</string>
<string name="sandbox_back_to_origin_game">Zum Original-Spiel</string>
```

---

## Akzeptanzkriterien

- [ ] Move-to-Sandbox-Icon erscheint in RoundSummary pro Spieler
- [ ] Move-to-Sandbox-Icon in GameSummary in Rundentabelle, pro Zelle
- [ ] Move-to-Sandbox-Icon in GameInProgress Spielstand-Tabelle (nur abgeschlossene Runden)
- [ ] Move-to-Sandbox-Icon in PlayerStats letzte Spiele
- [ ] Move-to-Sandbox-Icon in CardStats höchster Beitrag
- [ ] Tap öffnet Sandbox mit korrekt vorbefüllter Hand
- [ ] Joker-Belegungen werden korrekt übernommen
- [ ] Origin-Banner zeigt: Spieler-Name, Runden-Nummer, Datum
- [ ] Score in Sandbox matched mit RoundSummary-Score (zur Verifikation)
- [ ] User kann Karten ändern, Score aktualisiert sich live
- [ ] Sandbox-Inhalte aus Historie betreffen **nicht** die echte Historie
- [ ] Reset in Sandbox leert auch Origin-Banner
- [ ] Sandbox aus Home-Tab funktioniert weiterhin (leerer Start)
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **Discard-Pile**: im MVP wird Mittelfeld eh nicht erfasst (keine Kamera). Discard ist also immer leer beim Move-to-Sandbox. Code ist trotzdem schon vorbereitet für Phase 2.
- **Round-Anzeige im Banner**: "Spiel vom 12.10.2025" als Fallback wenn kein `displayName`
- **Performance**: Vorbefüllung lädt aus DB, sollte < 100 ms dauern. Während des Ladens kurzer Spinner.
