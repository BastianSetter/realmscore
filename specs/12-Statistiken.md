# Phase 12 – Statistiken

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 11 ist abgeschlossen. Historie zeigt alle Spiele.

---

## Kontext (kurz)

Der `StatsPlaceholderScreen` wird durch echte **Statistik-Screens** ersetzt. Es gibt einen Overview-Screen im Stats-Tab plus Detail-Screens fuer Spieler, Karten und Head-to-Head.

Volle Vision: siehe `00-vision.md`. Spec-Details siehe `10_statistiken.md` im Specs-Ordner.

---

## Scope dieser Phase

### Drin
- `StatsOverviewScreen` (im Stats-Tab) mit:
  - Globale Übersicht (Spiele-Counter, etc.)
  - Spieler-Rangliste (sortiert nach Siegquote)
  - Karten-Hits (beliebteste, verschmähteste, wertvollste)
- `PlayerStatsScreen` (Detail pro Spieler)
- `CardStatsOverviewScreen` (Liste aller Karten mit Stats)
- `CardStatsScreen` (Detail pro Karte)
- `HeadToHeadScreen` (zwei Spieler im Vergleich)
- `StatsRepository` + alle Use Cases
- Nach Datenqualität ("nur X von Y Runden mit Mittelfeld") Hinweis-Banner

### Explizit NICHT drin
- Kein Zeitraum-Filter (Phase 2)
- Kein Random-Stat-Graph im Home-Tab (Phase 13)
- Keine Move-to-Sandbox-Funktion in Stats (Phase 14)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenue → Stats-Tab
2. `StatsOverviewScreen` zeigt: Globale Übersicht + Spieler-Rangliste + Karten-Hits
3. Tap auf einen Spieler → `PlayerStatsScreen` mit Detail-Stats
4. Aus PlayerStats: Tap auf "Wer-gegen-wen" → Liste, dann Tap auf Gegner → `HeadToHeadScreen`
5. Aus StatsOverview: Tap auf "Alle Karten ansehen" → `CardStatsOverviewScreen`
6. Tap auf eine Karte → `CardStatsScreen` mit Detail-Stats
7. Wenn keine oder wenige Daten: Empty State / Hinweis "Spiele ein paar Runden..."
8. Wenn Datenbasis unvollständig (kein Mittelfeld erfasst): Banner zur Transparenz

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies. Compose Canvas fuer Mini-Charts.

---

## Architektur-Vorgaben

> **Wichtig:** Detaillierte Spec mit allen Use Cases, Berechnungen und SQL-Beispielen liegt in `10_statistiken.md` im Specs-Ordner. Diese Phasen-Datei gibt nur die Struktur und das Wichtigste vor. Die Umsetzung darf sich an dieser ausfuehrlichen Spec orientieren.

### Stats-Repository + Use Cases

```kotlin
interface StatsRepository {
    suspend fun getGlobalStats(): GlobalStats
    suspend fun getPlayerStats(profileId: String): PlayerStats
    suspend fun getCardStats(cardKey: String): CardStats
    suspend fun getCardStatsOverview(sortBy: CardStatsSort): List<CardStatsRow>
    suspend fun getHeadToHeadStats(profileIdA: String, profileIdB: String): HeadToHeadStats
}

data class GlobalStats(
    val totalGamesPlayed: Int,
    val totalRoundsPlayed: Int,
    val uniquePlayers: Int,
    val avgGameDurationMinutes: Long
)

data class PlayerStats(
    val profile: Profile,
    val gamesPlayed: Int,
    val winCount: Int,
    val winRate: Double,
    val avgScorePerHand: Double,
    val bestSingleHandScore: Int,
    val favoriteCards: List<CardWithCount>,    // Top 5
    val opponents: List<OpponentStat>,
    val recentGames: List<RecentGameEntry>     // letzte 5
)

data class CardStats(
    val card: CardDefinition,
    val inHandCount: Int,
    val inDiscardCount: Int,
    val handShare: Double,                     // Anteil basierend auf scanned-discard-Runden
    val avgContribution: Double,
    val highestSingleContribution: Int,
    val highestContext: ContributionContext?,
    val playersWhoPlayedIt: List<PlayerWithCount>,
    val frequentPartners: List<CardWithCount>  // Top 5 Karten die oft mit dieser zusammenliegen
)

data class HeadToHeadStats(
    val playerA: Profile,
    val playerB: Profile,
    val gamesTogether: Int,
    val winsA: Int,
    val winsB: Int,
    val avgScoreA: Double,
    val avgScoreB: Double,
    val sharedGameHistory: List<SharedGameEntry>
)
```

### StatsOverviewScreen

```kotlin
@Composable
fun StatsOverviewScreen(
    onOpenPlayer: (profileId: String) -> Unit,
    onOpenCard: (cardKey: String) -> Unit,
    onOpenCardOverview: () -> Unit
) {
    val vm: StatsOverviewViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    if (state.isEmpty) {
        EmptyStatsScreen()
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GlobalStatsCard(state.global)
        Spacer(Modifier.height(16.dp))
        PlayerRanking(state.playerRanking, onOpenPlayer)
        Spacer(Modifier.height(16.dp))
        CardHits(state.cardHits, onOpenCard)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenCardOverview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stats_overview_all_cards))
        }
    }
}
```

### PlayerStatsScreen

Layout-Sketch:
- Header: Avatar + Name
- 2x2-Grid an Kennzahlen (Spiele, Siegquote, Ø Punkte, Bester Einzelwert)
- Punkte-Trend (LineChart in Compose Canvas)
- Histogramm der Hand-Werte
- "Lieblingskarten" – Top 5
- "Wer-gegen-wen" – Liste der Gegner mit "X Siege / Y Niederlagen"
- Letzte Spiele

### CardStatsOverviewScreen

LazyColumn mit allen 53 Karten:
- Sortier-Dropdown oben ("Beliebtheit", "Ø Punkte", etc.)
- Pro Zeile: Kartenname, Suit-Badge, Anzahl in Hand, Anzahl im Mittelfeld, Hand-Anteil als Bar, Ø Punkte

### CardStatsScreen

- Header: Karten-Bild (falls vorhanden) + Name + Suit + Regeltext + Basisstärke
- Kernzahlen: in Hand X-mal, im Mittelfeld Y-mal, Hand-Anteil Z%, Ø Beitrag, höchster/niedrigster
- "Beliebtheit pro Spieler" – Liste
- "Häufigste Kombi-Partner" – Top 5
- Punkte-Beitrag-Verteilung (vereinfachtes Histogramm)

### HeadToHeadScreen

- Header: zwei Avatare gegenüber
- "X Spiele gemeinsam"
- Siege-Verhältnis
- Ø Punkte beide
- Liste gemeinsamer Spiele
- Karten-Vergleich (welche Karten hat A öfter, welche B)

### Datenqualitäts-Banner

Auf `CardStatsScreen` und `CardStatsOverviewScreen` immer:
- "Basierend auf X Runden, davon Y mit Mittelfeld-Scan"
- Bei sehr niedrigem Anteil: zusätzlicher Hinweis "Stats werden zuverlässiger mit Mittelfeld-Erfassung"

### Routes

```kotlin
const val STATS_OVERVIEW = "stats"  // = TAB_STATS
const val PLAYER_STATS = "stats/player/{profileId}"
const val CARD_STATS = "stats/card/{cardKey}"
const val CARD_STATS_OVERVIEW = "stats/cards"
const val HEAD_TO_HEAD = "stats/h2h/{profileIdA}/{profileIdB}"
```

Die Sub-Routes laufen unter dem Stats-Tab im NavGraph.

### Mini-Charts in Compose Canvas

Drei Chart-Typen brauchen wir:
- **Horizontal Stacked Bar** (für Hand-Anteile): einfache Box mit Sub-Boxen
- **Vertical Bar Chart** (Histogramm): `Canvas` mit Rechtecken
- **Line Chart** (Trend): `Canvas` mit `drawLine`-Calls

Implementierung in `ui/components/charts/`:
```kotlin
@Composable
fun StackedBar(parts: List<BarPart>, modifier: Modifier = Modifier)

@Composable
fun BarChart(data: List<Int>, labels: List<String>, modifier: Modifier = Modifier)

@Composable
fun LineChart(points: List<Float>, modifier: Modifier = Modifier)
```

### Strings (Ergaenzungen)

```xml
<string name="stats_overview_title">Statistiken</string>
<string name="stats_overview_global">Globale Übersicht</string>
<string name="stats_overview_player_ranking">Spieler-Rangliste</string>
<string name="stats_overview_card_hits">Karten-Hits</string>
<string name="stats_overview_all_cards">Alle Karten ansehen</string>
<string name="stats_global_games">Gespielte Spiele</string>
<string name="stats_global_rounds">Gespielte Runden</string>
<string name="stats_global_players">Unterschiedliche Spieler</string>
<string name="stats_global_avg_duration">Ø Spieldauer: %1$d Min</string>

<string name="stats_player_games_played">Spiele</string>
<string name="stats_player_win_rate">Siegquote</string>
<string name="stats_player_avg_score">Ø Punkte</string>
<string name="stats_player_best_score">Beste Hand</string>

<string name="stats_card_in_hand">In Hand: %1$d</string>
<string name="stats_card_in_discard">Im Mittelfeld: %1$d</string>
<string name="stats_card_avg_contribution">Ø Beitrag: %1$.1f</string>
<string name="stats_card_data_warning">Basierend auf %1$d Runden, davon %2$d mit Mittelfeld-Scan</string>

<string name="stats_h2h_games_together">%1$d Spiele gemeinsam gespielt</string>
<string name="stats_empty_title">Noch keine Statistiken</string>
<string name="stats_empty_body">Spiele ein paar Runden, dann gibt's hier Insights.</string>
```

---

## Akzeptanzkriterien

- [ ] Stats-Tab zeigt `StatsOverviewScreen` (nicht mehr Platzhalter)
- [ ] Bei Empty State: korrekter Hinweis
- [ ] Globale Statistik korrekt berechnet
- [ ] Spieler-Rangliste sortiert nach Siegquote
- [ ] Karten-Hits zeigt 3 Cards (beliebteste, verschmähteste, wertvollste)
- [ ] `PlayerStatsScreen` zeigt alle Sektionen
- [ ] `CardStatsOverviewScreen` zeigt sortierbar alle Karten
- [ ] `CardStatsScreen` zeigt Detail-Stats inkl. Daten-Qualitäts-Hinweis
- [ ] `HeadToHeadScreen` zeigt Vergleich korrekt
- [ ] Mini-Charts rendern fluessig
- [ ] Daten-Qualitäts-Banner auf Card-Stats-Screens
- [ ] Aus PlayerStats heraus: Wer-gegen-wen-Navigation funktioniert
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich
- [ ] Unit-Tests fuer Stats-Berechnungen (mindestens 5 Test-Cases mit synthetischen Daten)

---

## Hinweise

- **Performance**: Stats werden on-demand berechnet via Repository-Aufrufe. Bei sehr grossen Daten-Mengen (>200 Spiele) ggf. Caching in Phase 2.
- **Komplexe Queries** ("haeufigste Kombi-Partner") koennen in SQL umstaendlich sein. Wenn zu komplex: in Kotlin aggregieren via Roh-Daten-Laden.
- **Empty-State-Schwelle**: < 3 abgeschlossene Spiele → Empty State im Overview
