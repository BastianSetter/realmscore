# HANDOFF — Profil-Rework (Device-/Profile-ID + Zeiger-Merge)

> Stand: 2026-06-21, Branch `v1.3.0`. **Nur Design-Stand festgehalten — noch keine Implementierung,
> noch kein Schritt-Plan.** Nächster Schritt wäre ein konkreter Umsetzungsplan (auf Zuruf).

## Ziel

Profil-Speicherung & -Zuweisung umbauen, sodass Merges zur **Laufzeit als Zeiger** aufgelöst werden
(nicht-destruktiv) statt wie heute die Historie physisch umzuschreiben. Damit wird die Backup-/P2P-
Logik deutlich einfacher (kaum noch Reconciliation nötig — Identität ist global eindeutig, Merges
reisen als Spalte mit).

## Getroffene Entscheidungen (vom User bestätigt)

| Thema | Entscheidung |
|---|---|
| **Identität/PK** | Surrogat-PK `id` **bleibt**; zusätzlich Spalten `deviceId` + `profileId`. Konvention `id = "$deviceId:$profileId"`. **Keine** FK-Änderungen an `game_participants`/`round_results`. |
| **Migration** | **Destruktiver DB-Wipe** (v8 → v9 über bestehenden `fallbackToDestructiveMigration`). Pre-Release, kein Migrationscode, keine Datenerhaltung nötig. |
| **Merge-Feld** | **Ein** nullbares `mergeTargetId: String?` (Referenz auf `profile.id`-Surrogat). Nicht zwei Spalten. |
| **Namens-Eindeutigkeit** | **Gelockert** — Duplikate erlaubt. `existsByName`/`countByName`-Erzwingung + `NAME_EXISTS`/`RenameResult.EXISTS` entfallen. |

## Zielmodell

- Identität explizit **(deviceId, profileId)** statt namens-Hash. `name` = reines Anzeigefeld.
- **Owner**: `profileId == deviceId` → an joinendem Profil sofort erkennbar, ob es der Owner des
  anderen Geräts ist.
- Neues Feld **`mergeTargetId`** → Merge ist ein Zeiger, Auflösung zur Laufzeit.
- **Vier Zustände** (abgeleitet, keine Enum-Spalte nötig):
  - **Owner** (`profileId == deviceId`)
  - **Merged** (`mergeTargetId != null`)
  - **Archiviert** (`isArchived`)
  - **Aktiv** (Rest)
- **Auswahl (NewGame)**: Merged **und** Archivierte fallen raus.
- **Stats**: Merged zählen **unter dem Target**; Archivierte zählen **nirgends** (Verhaltensänderung
  ggü. heute, wo Archivierte noch zählen).
- **Keine Ketten**: Target eines Targets → flach aufs Kettenende umbiegen; vorhandene Zeiger
  nachziehen; Zyklenschutz.
- **Aktionen je Zustand**: Aktiv = Name/Farbe/Merge/Archivieren · Archiviert = nur
  Entarchivieren+Merge · Merged = nur Unmerge · Owner = nur Name/Farbe (nie archiviert/gemergt, darf
  aber **Ziel** eines Merges sein).

## Bestätigte Semantik-Annahmen

1. Archiviert zählt **nicht** in Stats (bewusste Änderung).
2. Doppelsitz in einem Spiel (zwei gemergte Profile im selben Spiel) → beide Hände werden unter dem
   Target **summiert** (gleiches Verhalten wie der heutige destruktive Merge, vgl. `ProfileMergeTest`).
3. Dangling Merge-Target (Import/P2P zeigt auf lokal unbekanntes Profil) → Zeiger bleibt, Auflösung
   **ignoriert** unbekannte Targets bis das Ziel auftaucht (lazy).
4. Owner darf **Ziel** eines Merges sein, aber selbst nie gemergt/archiviert.

## Auswirkungen je Bereich (Ist → Soll)

### Schema (DB v9, Wipe)
- `ProfileEntity`: + `deviceId`, `profileId`, `mergeTargetId: String?`. `isArchived`/`archivedAt`
  bleiben. FKs unverändert.
- `device_profile_mappings` (Entity/DAO/Repo/DI) voraussichtlich **entfernbar** — durch Merge-Target
  abgelöst.

### Was wegfällt
- `ProfileRepositoryImpl.mergeProfiles` + die destruktiven DAO-Queries
  `deleteConflictingParticipants` / `reassignParticipants` / `reassignRoundResults` /
  `countCombinedGames`.
- `generateProfileId` (namensbasiert) → zufällige `profileId` (UUID), Owner = deviceId.
- `existsByName`/`countByName`-Erzwingung; `RenameResult.EXISTS`; `AddError.NAME_EXISTS`.
- `NewGameViewModel.resolveLocalProfile` / `mergeJoinedParticipants` stark vereinfacht (fremde
  Profile werden unverändert übernommen — kein Remap auf lokale Ids mehr nötig).
- `BackupRepositoryImpl.reconcileSelfSeat` als destruktiver P2P-Sonderpfad → Pointer-Merge oder
  ganz raus (Trigger: `SessionManager.kt:548`).
- `ProfileMergeTest` (instrumentiert) muss neu geschrieben werden.

### Was neu kommt
- Repo: `setMergeTarget(id, targetId)` (Kettenkollaps + Zyklenschutz + vorhandene Zeiger nachziehen,
  in einer Transaktion) und `clearMergeTarget(id)` (Unmerge). Merge/Unmerge fassen `updatedAt` an
  (Stats-Cache-Fingerprint).
- **Stats-Kanonisierung einmal in `StatsRepositoryImpl.buildSnapshot`**: Map
  `profileId → canonicalId` auflösen, archivierte Profile **inkl. Results** rausfiltern, Ids in
  `participantsByGame`/`resultsBy…` ersetzen, `profilesById` nur mit kanonischen Profilen füllen.
  → `StatsCalculator` bleibt praktisch unverändert.
- DAO: `observeOwner`/`observeActive`/`observeMerged`/`observeArchived`; Auswahl-Queries
  (`getActiveByPrefix`/`searchByNamePrefix`/`suggestProfiles`) zusätzlich „kein mergeTargetId".
- UI Profilverwaltung: **4 Sektionen** + zustandsabhängige Aktionen; bei Merged Text
  „verschmolzen in <Target-Name>".
- Join-Screen (`JoinSessionScreen`/-`ViewModel`): „Lokales Profil zuweisen"-Button → setzt
  `mergeTargetId` des einkommenden Profils auf ein vorhandenes **aktives** lokales Profil
  (Auswahlliste = alle aktiven, nicht-merged Profile, **geräteunabhängig**).
- Backup: `schemaVersion` → 2, neue Felder in `BackupProfile`; Import wird reines
  skip-if-exists/LWW (keine Reconciliation).

## Betroffene Dateien (gesichtet)

- `data/db/entity/ProfileEntity.kt`, `data/db/dao/ProfileDao.kt`,
  `data/repository/ProfileRepositoryImpl.kt`, `domain/model/Profile.kt`
- `data/db/AppDatabase.kt` (Version-Bump), `di/AppContainer.kt`
- `data/db/entity/DeviceProfileMappingEntity.kt` + DAO + `data/repository/DeviceProfileMappingRepositoryImpl.kt` (Entfernung)
- `data/repository/StatsRepositoryImpl.kt`, `domain/stats/StatsCalculator.kt`
- `ui/newgame/NewGameViewModel.kt`, `ui/tabs/settings/ProfileManagementViewModel.kt` + `…Screen.kt`
- `ui/p2p/JoinSessionViewModel.kt` + `JoinSessionScreen.kt`, `data/p2p/SessionManager.kt`
- `data/repository/BackupRepositoryImpl.kt`, `domain/backup/BackupModels.kt`
- `androidTest/.../ProfileMergeTest.kt` (neu schreiben)
- strings (NAME_EXISTS/rename-error-exists entfernen; neue Section-/Merge-Texte)

## Empfohlene Umsetzungsreihenfolge (für später)

Schema → Repo/Merge (Zeiger + Normalisierung) → Stats-Kanonisierung → UI (4 Sektionen + Auswahl-Filter)
→ P2P/Backup-Vereinfachung → Tests.
