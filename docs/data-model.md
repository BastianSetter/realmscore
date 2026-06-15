# Datenmodell

Room-DB `fantasy_realms.db`, definiert in `data/db/AppDatabase.kt`. **Version 7**, `exportSchema = true`
(Schemas in `app/schemas/`).

## Kern-Prinzip: State aus Historie

Der **Spielzustand** (Punktestände, Sieger, ob das Spiel vorbei ist) wird **nicht** persistiert,
sondern bei Bedarf aus den Runden-/Karten-Tabellen berechnet (`GetGameStateUseCase`,
`StatsRepository`). Die einzige persistierte Zustandsvariable ist `Game.closedAt`
(offen / geschlossen) plus `closedReason`. Vorteil: Live-Stand und Statistiken nutzen dieselbe
Datenquelle, kein Drift möglich.

## Entities

Acht Entities (`data/db/entity/`). Alle schreibenden Tabellen tragen `createdAt`, `updatedAt` und
(außer Join-Tabellen) `originDeviceId` für die spätere Sync-Fähigkeit.

### `profile` — `ProfileEntity`
`id` (PK, deterministische SHA-256), `name`, `colorArgb`, `isLocalOwner`, `isArchived`,
`archivedAt?`, Timestamps, `originDeviceId`.
- Genau **ein** `isLocalOwner = true` pro Gerät (Owner via Onboarding).
- Andere Spieler werden implizit beim Spielanlegen erzeugt. Archivierung statt Löschen.

### `games` — `GameEntity`
`id` (PK), `displayName?`, `mode`, `targetRounds?`, `targetPoints?`, `startedAt`, `closedAt?`,
`closedReason?`, Timestamps, `originDeviceId`.
- `mode`: `GameMode` = `FIXED_ROUNDS` | `POINT_LIMIT`
- `closedReason`: `ClosedReason` = `COMPLETED` | `ABANDONED`

### `game_participants` — `GameParticipantEntity`
Composite-PK (`gameId`, `profileId`), `seatOrder`, `lastScanOrder?`.
FK auf `games` (CASCADE) und `profile`.

### `rounds` — `RoundEntity`
`id` (PK), `gameId` (FK→games, CASCADE), `roundNumber`, `startedAt`, `completedAt?`,
`discardScanned`, Timestamps, `originDeviceId`.
- `discardScanned` = ob das Mittelfeld (Discard-Pile) für diese Runde erfasst wurde.

### `round_results` — `RoundResultEntity`
`id` (PK), `roundId` (FK→rounds, CASCADE), `profileId` (FK→profile), `totalScore`, Timestamps,
`originDeviceId`.
- Ein Ergebnis pro Spieler pro Runde. `totalScore` ist der gespeicherte Endwert der Hand.

### `hand_cards` — `HandCardEntity`
`id` (PK), `roundResultId` (FK→round_results, CASCADE), `cardKey`, `position`,
`jokerTargetCardKey?`, `jokerTargetSuit?`, Timestamps.
- Die 7 (bzw. 8 mit Totenbeschwörer-Pull) Karten einer Spielerhand.
- Joker-/Spielerwahlen werden hier persistiert (`jokerTargetCardKey`, `jokerTargetSuit`) und beim
  Neu-Scoren in `JokerAssignment`s übersetzt.

### `discard_cards` — `DiscardCardEntity`
`id` (PK), `roundId` (FK→rounds, CASCADE), `cardKey`, `position`, Timestamps, `originDeviceId`.
- Manuell erfasstes Mittelfeld (Phase 20). Nötig für Karten-Statistiken und um den
  Totenbeschwörer-Pull auf die tatsächlich abgelegten Karten einzugrenzen.

### `sandbox_favorites` — `SandboxFavoriteEntity`
`id` (PK), `number`, `name?`, `cardsJson`, Timestamps, `originDeviceId`.
- Gespeicherte Sandbox-Hand. Die variabel lange Hand liegt JSON-serialisiert in `cardsJson`
  (Liste von `FavoriteCardDto`) statt in einer Kind-Tabelle.
- `name` (nullable Freitext) kam mit Migration 6→7 dazu; `null` ⇒ UI zeigt die `number`.

## DAOs

`data/db/dao/`: `ProfileDao`, `GameDao`, `RoundDao`, `RoundResultDao`, `HandCardDao`,
`DiscardCardDao`, `SandboxFavoriteDao`. Zusätzliche Query-Projektionen als eigene Klassen:
`GameScoreTotal`, `ProfileSharedGame`.

## Migrationen & destruktiver Fallback ⚠️

`data/db/migration/Migrations.kt` enthält bislang **eine** echte Migration:

- `MIGRATION_6_7` — fügt die Spalte `name` zu `sandbox_favorites` hinzu (spec 25.6).

Registriert in `AppContainer.database`. **Wichtig:** die DB nutzt zusätzlich
`fallbackToDestructiveMigration(dropAllTables = true)`. Jeder Versions-Bump ohne passende
Migration **löscht alle Nutzerdaten**. Das ist vor dem öffentlichen Release akzeptabel, aber:

> **TODO vor dem ersten echten Release** (siehe Kommentar in `AppContainer`): destruktiven Fallback
> durch echte Migrationen (oder Room-Auto-Migrations) ersetzen, sonst zerstört eine un-migrierte
> Schema-Änderung die Spiele/Profile/Stats der Nutzer.

## Domain-Modelle

`domain/model/` spiegelt die Entities als reine Kotlin-Datenklassen (`Game`, `Round`, `RoundResult`,
`HandCard`, `Profile`, `GameParticipant`, `SandboxFavorite`) mit `toDomain()`/`fromDomain()`-Mappern
auf den Entity-Klassen. Karten-spezifische Modelle (`CardDefinition`, `Suit`, `JokerType`,
`CardDisplay`) siehe `scoring-engine.md`.
