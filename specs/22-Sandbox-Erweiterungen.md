# Phase 22 – Sandbox-Erweiterungen (Favoriten + Multi-Hand)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 21 abgeschlossen.

---

## Kontext (kurz)

Zwei Erweiterungen der Sandbox:
1. **Favoriten:** Hände speichern und später wieder laden (automatisch durchnummeriert)
2. **Multi-Hand:** Zwei Hände nebeneinander vergleichen (Score + Karten-Details)

---

## Scope

### Drin
- Favoriten: speichern, laden, löschen (kein Name, durchnummeriert)
- Multi-Hand: Split-Screen mit zwei Händen nebeneinander
- Score-Vergleich oben, Karten-Details in der jeweiligen Hand
- Ring-Visualisierung nur bei Einzelhand (nicht im Multi-Hand-Modus)

### Explizit NICHT drin
- Keine Favoriten-Namen/Notizen
- Keine mehr als 2 Hände gleichzeitig

---

## Was am Ende funktionieren muss

**Favoriten:**
1. Sandbox mit 7 Karten → "Speichern"-Button → Favorit #1 angelegt
2. Zweiter Favorit → Favorit #2 usw.
3. Home-Tab oder Sandbox: "Favoriten"-Liste → Tap → Hand wird geladen
4. Favorit aus Liste löschen

**Multi-Hand:**
1. In der Sandbox: Button "Vergleichen" → Split-Screen öffnet sich
2. Linke Hand: aktuelle Sandbox-Hand (vorbefüllt)
3. Rechte Hand: leer, via CardPicker befüllbar
4. Oben: Score-Vergleich ("Links: 87 | Rechts: 112")
5. In den Karten: contributedScore (brutto) pro Karte
6. Tap auf Karte → aufklappen (gleich wie normale Sandbox-Karte)
7. "Zurück zur Einzelhand"-Button

---

## Favoriten: Datenmodell

```kotlin
// domain/model/SandboxFavorite.kt
data class SandboxFavorite(
    val id: String,             // UUID
    val number: Int,            // Anzeigereihenfolge (1, 2, 3, ...)
    val handCards: List<FavoriteCard>,
    val createdAt: Long
)

data class FavoriteCard(
    val position: Int,
    val cardKey: String,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?
)
```

Persistierung via **DataStore** (JSON-serialisiert) oder **Room-Tabelle** `sandbox_favorites`. Room ist sauberer für Listen → Room-Tabelle.

```kotlin
@Entity(tableName = "sandbox_favorites")
data class SandboxFavoriteEntity(
    @PrimaryKey val id: String,
    val number: Int,
    val cardsJson: String,      // JSON-serialisierte Liste von FavoriteCard
    val createdAt: Long
)
```

---

## Favoriten-UI

### Speichern
```kotlin
// In ScoreFooter / SandboxScreen unten:
OutlinedButton(onClick = { vm.saveFavorite() }) {
    Text(stringResource(R.string.sandbox_save_favorite))
}
```

Nach Speichern: Snackbar "Als Favorit #N gespeichert"

### Favoriten-Liste
Erreichbar über:
- Icon-Button in der SandboxScreen-TopBar (Lesezeichen-Icon)
- Kleine Card im Home-Tab "Sandbox" → "Favoriten ansehen"

```kotlin
@Composable
fun FavoritesList(
    favorites: List<SandboxFavorite>,
    onLoad: (SandboxFavorite) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn {
        items(favorites, key = { it.id }) { fav ->
            ListItem(
                headlineContent = { Text("Favorit #${fav.number}") },
                supportingContent = { Text(formatRelativeDate(fav.createdAt)) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onLoad(fav) }) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.sandbox_load_favorite))
                        }
                        IconButton(onClick = { onDelete(fav.id) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                    }
                }
            )
        }
    }
}
```

---

## Multi-Hand: Layout

```kotlin
@Composable
fun MultiHandScreen(
    initialLeft: SandboxUiState,
    onBack: () -> Unit
) {
    val vmLeft: SandboxViewModel = viewModel(...)
    val vmRight: SandboxViewModel = viewModel(...)

    // Links: mit initialLeft vorbefüllt
    // Rechts: leer

    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.multihand_title)) },
            navigationIcon = { BackButton(onBack) }
        )}
    ) { padding ->
        Column(Modifier.padding(padding)) {

            // Score-Vergleich-Header
            MultiHandScoreHeader(
                scoreLeft = vmLeft.uiState.value.score,
                scoreRight = vmRight.uiState.value.score
            )

            Divider()

            // Zwei Hände nebeneinander
            Row(Modifier.weight(1f)) {
                SandboxHandColumn(
                    modifier = Modifier.weight(1f),
                    vm = vmLeft,
                    label = stringResource(R.string.multihand_left)
                )
                VerticalDivider()
                SandboxHandColumn(
                    modifier = Modifier.weight(1f),
                    vm = vmRight,
                    label = stringResource(R.string.multihand_right)
                )
            }
        }
    }
}

@Composable
fun MultiHandScoreHeader(scoreLeft: Int, scoreRight: Int) {
    val winner = when {
        scoreLeft > scoreRight -> -1
        scoreRight > scoreLeft -> 1
        else -> 0
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ScoreDisplay(scoreLeft, isWinner = winner == -1)
        Text("vs", style = MaterialTheme.typography.titleMedium)
        ScoreDisplay(scoreRight, isWinner = winner == 1)
    }
}
```

### SandboxHandColumn

Kompakte Version des SandboxScreens:
- 7 Karten-Slots (vertikal statt horizontal wegen schmalem Layout)
- Pro Karte: Name + Suit + `contributedScore` (brutto)
- Tap → Klappt auf: Effekt-Details
- **Kein Ring** (Ring ist nur für Einzelhand)
- **Kein Joker-Optimal-Button** (kein Platz, user kann manuell setzen)

---

## Akzeptanzkriterien

- [ ] "Speichern"-Button in Sandbox verfügbar (nur wenn Hand vollständig)
- [ ] Favorit wird gespeichert und durchnummeriert
- [ ] Favoriten-Liste erreichbar und zeigt alle Favoriten
- [ ] Laden: Hand wird korrekt in Sandbox übernommen
- [ ] Löschen aus Favoriten-Liste funktioniert
- [ ] "Vergleichen"-Button öffnet Multi-Hand-Screen
- [ ] Linke Hand ist vorbefüllt mit aktueller Sandbox-Hand
- [ ] Rechte Hand ist leer, befüllbar via CardPicker
- [ ] Score-Vergleich oben aktualisiert sich live
- [ ] Tap auf Karte → Effekt-Details aufklappbar
- [ ] Ring-Visualisierung ist im Multi-Hand-Modus **nicht** vorhanden
- [ ] Ring-Visualisierung in der Einzelhand-Sandbox weiterhin verfügbar

---

## Strings (Ergänzungen)

```xml
<string name="sandbox_save_favorite">Als Favorit speichern</string>
<string name="sandbox_saved_as_favorite">Als Favorit #%1$d gespeichert</string>
<string name="sandbox_favorites_title">Favoriten</string>
<string name="sandbox_load_favorite">Laden</string>
<string name="multihand_title">Vergleich</string>
<string name="multihand_left">Hand A</string>
<string name="multihand_right">Hand B</string>
```
