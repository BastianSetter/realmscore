# Phase 24 — Code-Review-Findings

> **Status:** Review abgeschlossen. Dies ist **keine Spezifikation**, sondern ein Review-Bericht
> aus Senior-Developer-Sicht über den Code-Stand bis Spec 23 (inkl. undokumentierter Bugfixes).
> Findings sind nach Schweregrad sortiert; jeder Eintrag nennt Datei(en):Zeile, Beobachtung,
> Auswirkung und Vorschlag. Nichts davon wurde verändert — das ist bewusst eine reine Bestandsaufnahme.

Reviewter Umfang: Scoring-Engine + Regeln, Solver, Daten-/Repository-Layer, Backup/Export (Spec 23),
Stats, zentrale ViewModels (RoundCapture, Reveal, Stats), DI, Manifest, String-Ressourcen.

---

## Umsetzungsstatus (Stand 2026-06-11)

Alle Findings umgesetzt; `:app:compileDebugKotlin` grün.

| Finding | Status | Umsetzung |
|--------|--------|-----------|
| **H1** | ✅ behoben | Stats nutzt jetzt den gemeinsamen `toScoringChoices()`-Mapper → Totenbeschwörer-Pick fließt korrekt über `playerChoices` ein. |
| **M1** | ✅ behoben | `StatsRepositoryImpl` cached den Snapshot, invalidiert über einen Fingerprint (Counts + `MAX(updatedAt)`). Neue Cheap-Queries in `Game/Round/RoundResult/ProfileDao`. |
| **M2** | ✅ (andere Session) | Merge auf Runden-Ebene implementiert (`BackupRepositoryImpl`, `insertParticipantsIgnore`). |
| **M3** | 📝 dokumentiert | Bewusste Maintainer-Entscheidung: vorerst nur Warn-Kommentar/TODO an `AppContainer` (destruktiver Fallback), echte Migration vor dem ersten Release. |
| **L1** | ✅ behoben | Gemeinsamer `reconstructScoringChoices`-Core für `HandCardEntry` **und** `HandCard` (`@JvmName`-Overload). |
| **L2** | ✅ dokumentiert | Kommentar in `saveHand`: discard-Kontext bewusst weggelassen (Engine liest ihn nie, nur der Solver) → alle drei kanonischen Pfade identisch. |
| **L3** | ✅ behoben | Kommentar im `RingLayoutOptimizer` korrigiert (bis 7! = 5040 mit 8. Karte). |
| **L4** | ✅ behoben | `Log.w` im Stats-`catch` statt stillem `continue`. |
| **L5** | ✅ behoben | Mittelfeld-Draft wird im Observer re-hydratisiert (außer wenn gerade bearbeitet). |
| **L6** | ✅ behoben | `captured`-Auto-Markierung nutzt jetzt `canSubmit(id)` statt reiner Slot-Zählung. |

> Hinweis: Die Detailbeschreibungen unten bleiben als Begründung/Kontext erhalten.

---

## 🔴 Hoch — Korrektheit

### H1 — Stats-Neuberechnung ignoriert den Totenbeschwörer (divergierende Reconstruction-Logik)

**Datei:** `data/repository/StatsRepositoryImpl.kt:105–119`
(vgl. die kanonische Variante `domain/scoring/SavedHandChoices.kt:25` `toScoringChoices()`)

`SavedHandChoices.toScoringChoices()` ist laut eigenem Doc-Kommentar *„the single source of truth
shared by the capture, reveal and breakdown paths“*. RevealViewModel, RoundSummaryViewModel,
BreakdownViewModel und RoundCaptureViewModel nutzen ihn auch. **StatsRepositoryImpl nutzt ihn nicht**,
sondern inlined eine eigene Rekonstruktion über die Domain-`HandCard` — und diese hat den
Totenbeschwörer-Sonderfall **nicht**:

```kotlin
val assignments = cards.mapNotNull { entry ->
    val target = entry.jokerTargetCardKey ?: return@mapNotNull null   // <-- gilt auch für necromancer
    ...
    entry.cardKey to JokerAssignment(jokerKey = entry.cardKey, targetCardKey = target, ...)
}.toMap()
scoringEngine.score(ScoringInput(hand = hand, jokerAssignments = assignments))  // playerChoices fehlt
```

Der Totenbeschwörer hat `isJoker = false` (verifiziert in `assets/cards/base_game.json`). Folgen:
1. Der `JokerResolver` überspringt die erzeugte `JokerAssignment("necromancer", …)` (kein Joker).
2. `ScoringInput.playerChoices` bleibt Default → `ScoringEngine.effectiveNecromancerPickKey()` liefert
   `null` → die gezogene 8. Karte wird **gar nicht** eingefügt.

**Auswirkung:** Für jede Runde, in der ein Totenbeschwörer eine Karte gezogen hat, sind die in den
Karten-Statistiken angezeigten **Pro-Karte-Beiträge falsch** (zu niedrig): die gezogene Karte fehlt
komplett, und ihr Einfluss auf Suit-Boni anderer Karten (z. B. Sammler, Suit-Zählungen) entfällt.
Die in der DB gespeicherte `totalScore` ist korrekt (Capture/Reveal nutzen den richtigen Pfad), d. h.
die Summe der Pro-Karte-Beiträge stimmt dann nicht mehr mit der gespeicherten Rundensumme überein.

**Vorschlag:** Die Rekonstruktion vereinheitlichen. Entweder `toScoringChoices()` zusätzlich für
`List<HandCard>` anbieten (gemeinsames Interface/Mapper über `cardKey`/`jokerTargetCardKey`/
`jokerTargetSuit`), oder in StatsRepositoryImpl explizit den `necromancer`-Key nach
`PlayerChoices.necromancerPickKey` routen — exakt wie in `SavedHandChoices`. Ein Test, der
Capture-Score vs. `sum(perCard)` für eine Totenbeschwörer-Hand vergleicht, würde das absichern.

---

## 🟠 Mittel

### M1 — `buildSnapshot()` wird pro Stats-Abfrage komplett neu berechnet (kein Caching)

**Datei:** `data/repository/StatsRepositoryImpl.kt:42–95, 129–158`

Jede öffentliche Methode (`getOverview`, `getGlobalStats`, `getPlayerStats`, `getCardStats`,
`getCardStatsOverview`, `getHeadToHead`, `getClosestRoundEver`, `getMostPlayedPair`) ruft jeweils
`buildSnapshot()` neu auf. Ein Snapshot liest **die gesamte DB** (alle geschlossenen Spiele, Runden,
Ergebnisse, Handkarten) *und* re-scored über die `ScoringEngine` **jede Hand jedes Spiels**
(`computePerCardContributions`). Beim Öffnen der Stats-Übersicht plus der 7 `RandomStatProvider`
(`AppContainer.kt:149`) kann derselbe Snapshot in kurzer Folge **vielfach** gebaut werden.

**Auswirkung:** O(N)-Volllast pro Aufruf, Engine-Lauf (inkl. Blanking-Fixpunkt) pro Hand, mehrfach
redundant. Skaliert schlecht mit der Spielhistorie; auf älteren Geräten spürbar.

**Vorschlag:** Snapshot memoisieren und nur invalidieren, wenn sich relevante Daten ändern (ein Spiel
wird geschlossen). Z. B. ein `@Volatile`-Cache + Invalidierung im `GameRepository.closeGame`-Pfad,
oder die Stats reaktiv aus einem Room-`Flow` ableiten und `distinctUntilChanged` nutzen.

### M2 — Import merged nur auf Spiel-Granularität → neue Runden gehen beim Re-Import verloren

**Datei:** `data/repository/BackupRepositoryImpl.kt:111–147`

Konfliktstrategie: existiert die `gameId` bereits, wird das **ganze** Spiel übersprungen
(`gamesSkipped++; continue`). Wurde auf Gerät B an einem bereits exportierten Spiel weitergespielt
(neue Runden), importiert Gerät A diese neuen Runden **nie** — das Spiel „existiert ja schon“.

**Auswirkung:** Stilles Nicht-Übernehmen von Daten beim Backup-Austausch zwischen Geräten. Für ein
reines „Vollbackup auf neues Gerät“ (leere DB) unkritisch; für das im Doc-Kommentar angedeutete
„round-trip across devices“ aber eine Falle.

**Entscheidung (vom Maintainer):** Merge-Granularität auf **Runden/Ergebnisse** senken. Der folgende
Abschnitt ist die ausgearbeitete Vorgabe für die (noch nicht committete) Export/Import-Implementierung.

---

#### M2-Spezifikation: UUID-basierter Merge auf Runden-Ebene

**Ziel:** Ein Import bringt nicht nur *neue Spiele/Profile*, sondern auch *neue Runden innerhalb
bereits vorhandener Spiele* ein — ohne Duplikate, ohne lokale Daten zu zerstören. Granularität:
Profile (by id) → Spiele (by id) → **Runden (by id)** → Ergebnisse (by id) → Handkarten/Discard
(hängen an Runde bzw. Ergebnis und ziehen mit ihrem Parent mit).

##### Datenmodell-Kontext (verifiziert)

FK- und Cascade-Kette (alle `onDelete = CASCADE` außer den Profil-FKs):
`games → rounds → round_results → hand_cards`, dazu `rounds → discard_cards`,
sowie Profil-FKs von `round_results.profileId` und `game_participants.profileId` auf `profiles.id`.
**Konsequenz für die Insert-Reihenfolge:** strikt parent-vor-child (Profile zuerst, dann Game +
Participants, dann je Runde: Runde → Discards → je Ergebnis: Result → HandCards). Das tut
`insertGame` heute schon richtig — die Logik muss nur auf Runden-Ebene wiederverwendbar werden.

##### Neuer Algorithmus (ersetzt die `for (game …)`-Schleife in `importFromJson`)

Innerhalb der bestehenden `db.withTransaction { … }`:

1. **Profile** wie bisher (by id, skip-if-exists, `isLocalOwner = false`). Unverändert.

2. **Bestehende IDs einmalig laden** (Sets), damit kein Insert eine FK-/PK-Kollision auslöst:
   - `existingGameIds`  ← `gameDao.getAllGames().map { it.id }`
   - `existingRoundIds` ← `roundDao.getAll().map { it.id }`  (DAO existiert bereits)
   - `existingProfileIds` (schon vorhanden)

3. **Pro Spiel im Backup:**
   - **Spielzeile**
     - `id` neu  → `gameDao.insert(game.toEntity())`, danach Participants einfügen
       (`insertParticipants`). Zähler `gamesCreated++`.
     - `id` existiert → Spielzeile **nicht** anfassen (lokale Felder wie `displayName`,
       `closedReason`, `closedAt` gewinnen). Participants nur *ergänzen*, falls einzelne
       `(gameId, profileId)` fehlen — Insert mit `OnConflict.IGNORE` statt blindem `insertParticipants`
       (sonst PK-Verletzung auf `["gameId","profileId"]`). Zähler `gamesUpdated++` nur setzen, wenn in
       Schritt 4 tatsächlich neue Runden dazukamen.
   - **Pro Runde des Spiels (Schritt 4):**
     - `round.id` ∈ `existingRoundIds` → **überspringen** (Runde inkl. Discards/Results gilt als
       bereits vorhanden). `roundsSkipped++`.
     - sonst → vollständigen Teilbaum einfügen (Runde → Discards → Results → HandCards), exakt wie im
       heutigen `insertGame`-Innenteil, nur isoliert als `insertRoundSubtree(round, gameId)`.
       `roundsAdded++`. `round.id` zu `existingRoundIds` hinzufügen (falls dasselbe Backup eine id
       doppelt enthielte).

> **Warum Runden- und nicht Ergebnis-Granularität als Primärschnitt?** Eine Runde ist in dieser App
> atomar: sie wird erst „completed“, wenn *alle* Teilnehmer-Ergebnisse erfasst sind. Ein halb
> erfasstes, aber bereits exportiertes Runden-Fragment gibt es praktisch nicht. Runden-Skip-if-exists
> ist daher korrekt **und** einfach. Ergebnis-Level-Merge (Punkt „optional“ unten) ist nur nötig,
> falls künftig Ergebnisse einzeln nacherfasst/korrigiert und re-exportiert werden.

##### Referenzielle Integrität (wichtig — sonst kippt die ganze Transaktion)

- Ein importierter `round_result` referenziert `profileId`. Liegt dieses Profil **weder lokal noch im
  Backup** vor, wirft der Insert eine FK-Exception, die wegen des einen umschließenden
  `withTransaction` **den kompletten Import zurückrollt** (auch alle bis dahin erfolgreichen Spiele).
  → Vor dem Insert eines Result-Teilbaums prüfen, dass `profileId ∈ existingProfileIds` (nach
  Schritt 1). Fehlt es: Runde überspringen und in einem `skippedDueToMissingProfile`-Zähler erfassen,
  **nicht** die Exception fliegen lassen. (Defensive; bei wohlgeformten Backups tritt der Fall nie ein,
  weil Profile immer mitexportiert werden.)
- Insert-Reihenfolge strikt parent-vor-child beibehalten (s. o.).

##### DAO-Anpassungen

- `GameDao`: eine Insert-Variante für Participants mit `@Insert(onConflict = OnConflictStrategy.IGNORE)`
  (oder eine `getParticipantKeysForGame`-Query, um manuell zu diffen). IGNORE ist die schlankere Lösung.
- Sonst keine neuen Queries nötig: `roundDao.getAll()`, `gameDao.getAllGames()`, `profileDao.getAll()`
  reichen für die Existenz-Sets. (Falls die Spiel-/Rundenzahl groß wird, später gezielte
  `SELECT id …`-Projektionen statt voller Entities — für jetzt unkritisch.)

##### `ImportResult` erweitern (UI-Zusammenfassung wird sonst irreführend)

`gamesAdded/gamesSkipped` beschreibt einen Teil-Merge nicht mehr korrekt (ein Spiel kann „existierte
schon“ **und** „hat neue Runden bekommen“ gleichzeitig sein). Vorschlag — `domain/backup/BackupModels.kt`:

```kotlin
data class ImportResult(
    val profilesAdded: Int,
    val profilesSkipped: Int,
    val gamesCreated: Int,        // komplett neue Spiele
    val gamesUpdated: Int,        // existierende Spiele, in die neue Runden gemerged wurden
    val roundsAdded: Int,         // neue Runden gesamt
    val roundsSkipped: Int,       // bereits vorhandene Runden
    val roundsSkippedMissingProfile: Int = 0,
)
```

Und die Result-Toast/-Dialog-Texte (DE **und** EN, Keys spiegeln!) entsprechend anpassen, z. B.
„X neue Spiele, Y Runden zu bestehenden Spielen ergänzt, Z bereits vorhanden“.

##### Edge Cases / bewusste Nicht-Ziele

- **Editierte, aber gleich-`id`-Runde:** Runden-Skip-if-exists propagiert *Änderungen* an einer schon
  importierten Runde nicht. Das ist akzeptiert (echte Edits sind selten; Last-Writer-Wins erst mit
  Spec 29). Im Code als Kommentar festhalten.
- **`lastScanOrder`/`seatOrder` eines bestehenden Spiels:** bleiben lokal unangetastet (s. Schritt 3).
  Nicht über Backup-Werte überschreiben — sonst kann ein altes Backup neueren lokalen Stand
  zurückdrehen.
- **Atomarität:** Eine umschließende Transaktion beibehalten (Alles-oder-nichts auf Geräteebene). Die
  Profil-Vorabprüfung verhindert das FK-Rollback-Problem, ohne die Atomarität aufzugeben.

##### (Optional) Ergebnis-Level-Merge

Nur falls je gebraucht: zusätzlich zu neuen Runden auch *fehlende Ergebnisse* in einer bereits
existierenden Runde einfügen (`existingResultIds`-Set, skip-if-exists pro `round_result.id`, HandCards
ziehen mit). Erhöht die Komplexität ohne aktuellen Nutzen — erst mit konkretem Bedarf umsetzen.

##### Akzeptanzkriterien

1. Re-Import eines Backups in dieselbe DB ändert nichts (`roundsAdded == 0`, idempotent).
2. Gerät A exportiert Spiel G mit Runden 1–3; Gerät B spielt Runden 4–5 weiter und exportiert; Import
   auf A ergänzt G um Runden 4–5 (`gamesUpdated == 1`, `roundsAdded == 2`), Runden 1–3 unberührt.
3. Import eines Backups mit komplett neuem Spiel funktioniert wie bisher (`gamesCreated`).
4. Backup, dessen Result ein nicht vorhandenes Profil referenziert, lässt den Rest des Imports
   durchlaufen (kein globales Rollback) und wird im Zähler ausgewiesen.
5. Bestehender Test der Spiel-Granularität wird auf die neuen Zähler/Felder migriert; je ein Test für
   AK 1 und AK 2 ergänzt.

### M3 — `fallbackToDestructiveMigration(dropAllTables = true)` löscht bei jedem Schemawechsel alle Nutzerdaten

**Datei:** `di/AppContainer.kt:50`

Jede Room-Schemaänderung ohne Migration wischt die komplette DB. Bis zum Release ok als Entwicklungs-
Komfort, aber mit Spec 23 (Datenexport) und produktiver Nutzung wird das zur Datenverlustquelle —
gerade weil Nutzer:innen Historie/Statistiken aufbauen.

**Auswirkung:** Ein Versions-Update mit Schemaänderung vernichtet alle Spiele/Profile/Statistiken.

**Vorschlag:** Vor dem Release eine explizite Migrationsstrategie festlegen (echte `Migration`-Objekte
oder zumindest `exportSchema = true` + Auto-Migrations). Den destruktiven Fallback nur in Debug-Builds
belassen.

---

## 🟡 Niedrig — Smells & Refactor-Chancen

### L1 — Doppelte Reconstruction-Implementierung (Wurzel von H1)

`SavedHandChoices.toScoringChoices()` (über `HandCardEntry`) und der Inline-Block in
`StatsRepositoryImpl.computePerCardContributions` (über `HandCard`) sind zwei parallele
Implementierungen desselben Mappings, die bereits auseinandergelaufen sind (Necromancer). Ein
gemeinsamer Mapper über eine schmale Schnittstelle (`cardKey`, `jokerTargetCardKey`,
`jokerTargetSuit`) beseitigt H1 strukturell und verhindert künftige Divergenz.

### L2 — Persistierter Score in `saveHand` wird ohne Discard-Kontext berechnet (Fragilität)

**Datei:** `ui/game/RoundCaptureViewModel.kt:360–393`

`saveHand` baut den `ScoringInput` ohne `discardPile`/`discardScanned`, während `applyOptimal`
(`:306`) beide mitgibt. Aktuell **harmlos**, weil `ScoringEngine.buildNecromancerPick` die gezogene
Karte über `cardLookup` (nicht über `discardPile`) auflöst — und Reveal/Stats denselben Kontext
weglassen, also konsistent. Es ist aber eine latente Falle: sobald irgendeine Regel `ctx.discardPile`
liest, divergieren gespeicherte und neu berechnete Scores. Einheitlich denselben `ScoringInput`-Bau
über alle Pfade verwenden.

### L3 — Stale Doc-Kommentar im RingLayoutOptimizer

**Datei:** `domain/scoring/RingLayoutOptimizer.kt:11`

Kommentar: „all permutations (≤ 6! = 720)“. Mit der Totenbeschwörer-8.-Karte ist `n = 8`, also
`rest = 7` → `7! = 5040` Permutationen. Performance bleibt vertretbar, aber der Kommentar
(und die implizite Obergrenze) ist falsch. Aktualisieren.

### L4 — Stilles Verschlucken von Exceptions in der Stats-Berechnung

**Datei:** `data/repository/StatsRepositoryImpl.kt:120–124`

`catch (e: Exception) { continue }` ist als Schutz gegen „malformed legacy rows“ gedacht, maskiert
aber auch echte Regressionen (eine Engine-Exception bei einer validen Hand würde unbemerkt zu
fehlenden Stats führen). Mindestens loggen (z. B. `Log.w`), damit so etwas im Debug-Build auffällt.

### L5 — `RoundCaptureViewModel`: Discard-Draft wird vom Observer nicht nachgeladen

**Datei:** `ui/game/RoundCaptureViewModel.kt:168–178`

Der `combine(observeRoundById, observeDiscardCards)`-Collector aktualisiert `discardScanned`/
`discardCards` und ruft `rebuild()`, lädt aber `drafts[DISCARD_ID]` nicht neu. Da dieses ViewModel der
einzige Schreiber ist, in der Praxis unkritisch — aber die In-Memory-Draft und der DB-Stand können
theoretisch auseinanderlaufen. Entweder bewusst kommentieren oder den Discard-Draft im Collector
re-hydratisieren.

### L6 — `captured`-Auto-Markierung prüft nur Slot-Füllung, nicht Submit-Validität

**Datei:** `ui/game/RoundCaptureViewModel.kt:153–156`

Beim Wiedereintritt gilt ein Eintrag als `captured`, wenn `filled == requiredCount` — die
Joker-Zuweisungs-Prüfung aus `canSubmit` (`:395`) wird nicht gespiegelt. In der Praxis ungefährlich,
weil persistierte Hände immer über `canSubmit` liefen (also vollständige Joker-Zuweisungen haben). Als
Invariante dennoch fragil; bei künftigen Teil-Speichern könnte eine ungültige Hand fälschlich als
erledigt übersprungen werden. Idealerweise dieselbe Validität wie `canSubmit` verwenden.

---

## ✅ Positiv hervorzuheben

- **Scoring-Pipeline** (`ScoringEngine`, `BlankingResolver`, `PenaltyContext`) ist sauber als reine
  Kotlin-Domain ohne Android-Abhängigkeiten modelliert, gut dokumentiert und folgt nachvollziehbar der
  Regelbuch-Reihenfolge (Joker → Cancellation → Blanking-Fixpunkt → Boni → Strafen). Die „Clearing ist
  permanent, vor Blanking“-Invariante ist konsistent umgesetzt und kommentiert.
- **Übersetzung:** `values/strings.xml` und `values-en/strings.xml` haben identische Key-Mengen
  (389 Strings, je 1 Plural) — kein Drift, keine fehlenden Keys.
- **Backup-DTOs** tragen bewusst alle Sync-Metadaten (`originDeviceId`, Timestamps) für verlustfreien
  Round-Trip; Schema-Versionierung mit „too new“-Reject ist vorausschauend.
- **F-Droid-Tauglichkeit:** kein GMS/Firebase; FileProvider sauber, `exported=false`.

---

## Empfohlene Reihenfolge

1. **H1** beheben (echte Daten-/Anzeigefehler in Karten-Statistiken) — zusammen mit **L1** (gemeinsamer
   Mapper) in einem Aufwasch.
2. **M3** vor dem Release adressieren (Datenverlust-Risiko bei Schema-Migration).
3. **M1** (Snapshot-Caching) wenn die Historie wächst / Stats-Screen träge wird.
4. **M2** — Merge auf Runden-Ebene umsetzen (Maintainer-Entscheidung; ausgearbeitete Vorgabe oben).
   Legt zugleich das Fundament für Spec 29 (P2P-Sync).
5. Rest (L2–L6) als Aufräum-Tickets bei Gelegenheit.
