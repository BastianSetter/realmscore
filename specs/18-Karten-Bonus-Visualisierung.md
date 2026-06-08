# Phase 18 – Karten-Bonus-Visualisierung (Ring-Diagramm)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 17 abgeschlossen.

---

## Kontext (kurz)

Eine neue Visualisierung: Die 7 Handkarten werden als **Ring** angeordnet. Verbindungslinien zeigen welche Karten sich gegenseitig beeinflussen (Boni, Strafen, Blanking). Farbe und Stärke der Linie kodieren Art und Größe des Effekts. Der User sieht auf einem Blick die thematischen Cluster und Synergien seiner Hand.

Ersetzt das bisherige Bottom-Sheet nach dem Reveal (pro Spieler). Auch in der Sandbox verfügbar.

---

## Scope

### Drin
- `HandRingView` als wiederverwendbares Composable (Compose Canvas)
- Ring-Layout-Algorithmus (optimierte Kartenanordnung)
- Farbkodierung der Verbindungslinien
- Tap-Interaktion pro Karte
- Integration in Sandbox und RoundSummary (ersetzt Bottom-Sheet)

### Explizit NICHT drin
- Keine Übersicht aller 53 Karten gleichzeitig (nur die 7 der aktuellen Hand)
- Kein Multi-Hand-Modus (Phase 22)

---

## Was am Ende funktionieren muss

1. **In der Sandbox:** statt "Aufschlüsselung"-Button jetzt "Ring anzeigen"-Button
2. **Nach dem Reveal:** Tap auf Spieler-Card → öffnet Ring-View statt Bottom-Sheet
3. Ring zeigt 7 Karten im Kreis, beste Karte oben
4. Verbindungslinien zwischen Karten die sich beeinflussen
5. Linie grün = Bonus, rot = Strafe, Linienstärke ∝ Effektwert
6. Geblankte Karte: grau + halbtransparent
7. Tap auf Karte: zeigt Basisstärke auf der Karte + exakten Beitrag am Linienansatz

---

## Ring-Layout-Algorithmus

**Ziel:** 7 Karten im Ring so anordnen, dass die Summe der gewichteten Linienlängen minimal ist. Karten die stark verbunden sind, stehen nah beieinander.

**Algorithmus:**
1. Beste Karte (höchster `contributedScore`) fix auf Position 0 (oben, 12-Uhr)
2. Restliche 6 Positionen: **Brute-Force** über alle 6! = 720 Permutationen
3. Pro Permutation: berechne Kosten = Summe über alle Verbindungen von `|effekt| × arcDist(posA, posB)`
   - `arcDist`: kleinster Bogenwinkel zwischen zwei Positionen im Ring (0–3, da 6 mögliche Abstände bei 7 Positionen)
4. Permutation mit minimalen Kosten gewinnt
5. Bei Gleichstand: alphabetisch für Deterministismus

```kotlin
// domain/scoring/RingLayoutOptimizer.kt
object RingLayoutOptimizer {
    fun optimize(cards: List<CardDefinition>, connections: List<RingConnection>): List<Int> {
        // Gibt Reihenfolge der cards-Indizes zurueck (cards[result[0]] ist oben)
        val anchorIdx = cards.indexOfMaxBy { it.contributedScore }
        // Brute-Force ueber alle Permutationen der restlichen Indizes
        // Kostenberechnung: sum(|weight| * arcDist(posA, posB))
        // Gibt optimale Reihenfolge zurueck
    }
}

data class RingConnection(
    val fromCardIdx: Int,
    val toCardIdx: Int,
    val weight: Int,          // positiv = Bonus, negativ = Strafe
    val isBlanking: Boolean   // true wenn Blanking-Effekt
)
```

---

## HandRingView (Compose Canvas)

```kotlin
@Composable
fun HandRingView(
    cards: List<CardDefinition>,
    scoringResult: ScoringResult,
    modifier: Modifier = Modifier,
    onCardTap: ((Int) -> Unit)? = null
) {
    val connections = remember(scoringResult) { buildConnections(scoringResult) }
    val layout = remember(cards, connections) { RingLayoutOptimizer.optimize(cards, connections) }
    var tappedCardIdx by remember { mutableStateOf<Int?>(null) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) * 0.35f
        val cardRadius = minOf(size.width, size.height) * 0.1f

        // 1. Verbindungslinien zeichnen (unter den Karten)
        connections.forEach { conn ->
            drawConnection(conn, layout, center, radius, cardRadius)
        }

        // 2. Karten zeichnen (als Kreise mit Text)
        layout.forEachIndexed { pos, cardOriginalIdx ->
            val angle = (pos.toFloat() / 7f) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val cardCenter = Offset(
                center.x + radius * cos(angle),
                center.y + radius * sin(angle)
            )
            drawCardNode(
                card = cards[cardOriginalIdx],
                result = scoringResult.perCard[cardOriginalIdx],
                center = cardCenter,
                radius = cardRadius,
                isSelected = tappedCardIdx == cardOriginalIdx
            )
        }
    }
}
```

### Verbindungslinien-Darstellung

```kotlin
private fun DrawScope.drawConnection(
    conn: RingConnection,
    layout: List<Int>,
    center: Offset,
    ringRadius: Float,
    cardRadius: Float
) {
    val weight = conn.weight
    if (weight == 0) return

    val fromPos = layout.indexOf(conn.fromCardIdx)
    val toPos = layout.indexOf(conn.toCardIdx)
    val fromAngle = (fromPos.toFloat() / 7f) * 2f * PI.toFloat() - PI.toFloat() / 2f
    val toAngle = (toPos.toFloat() / 7f) * 2f * PI.toFloat() - PI.toFloat() / 2f

    val fromCenter = Offset(center.x + ringRadius * cos(fromAngle), center.y + ringRadius * sin(fromAngle))
    val toCenter = Offset(center.x + ringRadius * cos(toAngle), center.y + ringRadius * sin(toAngle))

    // Linienstärke: proportional zum Absolutwert, min 2dp, max 8dp
    val strokeWidth = (abs(weight).toFloat() / maxWeight * 6f + 2f).dp.toPx()

    // Farbe: grün für Bonus, rot für Strafe, grau für Blanking
    val color = when {
        conn.isBlanking -> Color.Gray
        weight > 0 -> Color(0xFF2E7D32)   // dunkelgrün, Stärke per Alpha
        else -> Color(0xFFC62828)           // dunkelrot
    }

    // Pfeil von fromCenter zu toCenter (unidirektional)
    drawLine(
        color = color,
        start = fromCenter + (toCenter - fromCenter).normalize() * cardRadius,
        end = toCenter - (toCenter - fromCenter).normalize() * (cardRadius + 8.dp.toPx()),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
    // Pfeilspitze als kleines gefülltes Dreieck
    drawArrowHead(color, toCenter, fromCenter, strokeWidth)
}
```

### Karten-Node-Darstellung

```kotlin
private fun DrawScope.drawCardNode(
    card: CardDefinition,
    result: CardScoreResult,
    center: Offset,
    radius: Float,
    isSelected: Boolean
) {
    val isBlanked = result.isBlanked
    val alpha = if (isBlanked) 0.35f else 1f
    val bgColor = if (isBlanked) Color.Gray else suitColor(card.suit)

    // Kreis
    drawCircle(color = bgColor.copy(alpha = alpha), radius = radius, center = center)
    drawCircle(color = Color.White.copy(alpha = alpha * 0.3f), radius = radius, center = center, style = Stroke(2f))

    // contributedScore (brutto)
    // Kartenname (kurz, 8-10 Zeichen)
    // Bei isSelected: zusaetzlich Basisstaerke

    // Die Texte werden via drawText (Canvas) oder als Composable via BoxWithConstraints gezeichnet
    // Empfohlen: Composable Layout statt Canvas-Text fuer bessere Lesbarkeit
}
```

> **Hinweis:** Für Text innerhalb von Canvas empfiehlt sich `drawText` mit `TextMeasurer` (Compose 1.4+). Alternativ: Positions berechnen und Text via Composable `Box` mit `absoluteOffset` überlagern.

### Tap-Interaktion

Der Canvas selbst ist nicht direkt tappable pro Position. Lösung:
- `Modifier.pointerInput(Unit)` mit `detectTapGestures`
- Berechne bei jedem Tap, welche Karten-Position am nächsten liegt
- Wenn Tap innerhalb `cardRadius * 1.5f` von einer Kartenposition: Toggle `tappedCardIdx`

### Ausklapp-Inhalt bei Tap

Wenn `tappedCardIdx != null`, erscheint eine Info-Card **außerhalb des Ring-Canvas** (darunter):
- Kartenname + Suit
- Basisstärke: X
- Pro Verbindungslinie an dieser Karte: "Von König: +10" / "Straft Hexe: -15"
- Genaue Effekt-Texte aus `EffectApplication.descriptionKey`

---

## Integration

### Sandbox

```kotlin
// SandboxScreen.kt
// Bisher: Button "Aufschlüsselung"
// Neu: Toggle-Button "Ring / Liste" (beide Ansichten weiterhin verfügbar)
// Standard: Ring
```

### RoundSummary

```kotlin
// Bisher: Tap auf PlayerSummaryCard → ScoreBreakdownSheet (ModalBottomSheet)
// Neu: Tap auf PlayerSummaryCard → öffnet HandRingView als FullScreen oder großes Sheet
```

Route:
```kotlin
const val HAND_RING = "round/{roundId}/player/{profileId}/ring"
fun handRingRoute(roundId: String, profileId: String) = "round/$roundId/player/$profileId/ring"
```

---

## Performance

- `RingLayoutOptimizer`: 720 Iterationen mit je ~7*7/2 = 21 Verbindungs-Checks → microseconds, kein Hintergrundthread nötig
- Canvas wird bei jedem Recompose neu gezeichnet. `remember` für Layout + Connections nutzen um unnötige Neuberechnungen zu vermeiden
- Animiertes Einblenden der Linien beim ersten Render: `animateFloatAsState` für Linienlänge (0→1), 300ms

---

## Akzeptanzkriterien

- [ ] Ring zeigt 7 Karten korrekt angeordnet (beste oben)
- [ ] Anordnung ist durch den Optimierungsalgorithmus verbindungsminimierend
- [ ] Verbindungslinien erscheinen nur wenn Effekt ≠ 0
- [ ] Pfeil zeigt Richtung (gebend → empfangend)
- [ ] Linienstärke proportional zum Effektwert
- [ ] Grün für Boni, Rot für Strafen
- [ ] Geblankte Karten grau + halbtransparent
- [ ] Tap auf Karte zeigt Basisstärke + Verbindungsdetails
- [ ] In Sandbox via Toggle erreichbar
- [ ] In RoundSummary statt Bottom-Sheet
- [ ] Einblendeanimation funktioniert flüssig
- [ ] Unit-Tests: `RingLayoutOptimizer.optimize` liefert erwartete Reihenfolgen für bekannte Testfälle
