# PLAN — Profil-Rework (Device-/Profile-ID + Zeiger-Merge)

> ✅ **ALLE PHASEN 0–8 IMPLEMENTIERT + AUF 2 PHONES VERIFIZIERT (2026-06-22).** Build grün:
> `assembleFdroidDebug`, `compileFdroidDebugAndroidTestKotlin`, `compileFdroidDebugUnitTestKotlin`;
> F-Droid-Reinheits-Check ohne Treffer. Golden-Path manuell getestet (Pixel 8 + Redmi Note 9 Pro) — ok.
> ⚠️ Erst-Start nach Update **wiped die DB** (v8→v9).
>
> **Nachbesserung (war nicht im Scope, in der Analyse übersehen):** History zeigte gemergte Profile als
> sich selbst. Gefixt in `HistoryViewModel.buildRawState` (Badges/Filter/Sieger + Totals folgen der
> mergeTargetId-Kette; Archivierte bleiben sichtbar) und `GameSummaryViewModel` (Anzeige-Name/-Farbe
> folgen dem Ziel, id bleibt für die Score-Tabellen erhalten). Beide via kleiner kettensicherer
> Auflösung (ohne Archiv-Ausschluss, da reine Anzeige). Device-verifiziert.
>
> Stand: 2026-06-22, Branch `v1.3.0`. Voller Umsetzungsplan. Design + Entscheidungen siehe
> `HANDOFF-Profile-Rework.md`. Reihenfolge ist so gewählt, dass nach **jeder** Phase kompiliert
> werden kann (`./gradlew.bat :app:compileFdroidDebugKotlin`).

## Eckpfeiler (entschieden)
- Surrogat-PK `id` bleibt; `id = "$deviceId:$profileId"`. Neue Spalten: `deviceId`, `profileId`,
  `mergeTargetId: String?`. **Keine** FK-Änderungen.
- DB v8 → **v9 mit destruktivem Wipe** (vorhandener `fallbackToDestructiveMigration`).
- Merge = **Zeiger** über `mergeTargetId` (Surrogat-Referenz). Auflösung zur Laufzeit.
- Namens-Duplikate **erlaubt**.
- Zustände abgeleitet: Owner (`profileId==deviceId`) / Merged (`mergeTargetId!=null`) /
  Archiviert (`isArchived`) / Aktiv (Rest).

---

## Phase 0 — Schema & Domain-Modell  ✅ ERLEDIGT (2026-06-22)
**Ziel:** Spalten da, DB-Version gebumpt, App kompiliert (noch ohne neue Logik).

> **Umsetzungs-Notiz:** Neue Felder mit Defaults (`deviceId=""`, `profileId=""`, `mergeTargetId=null`)
> ergänzt → alle bestehenden Call-Sites kompilieren weiter; Phase 1 belegt die Werte korrekt.
> **`device_profile_mappings`-Entfernung auf Phase 4/6 verschoben** (würde sonst NewGame-/JoinSession-VM
> brechen und den Build über mehrere Phasen rot lassen). Da Wipe + keine Zwischen-Releases bleibt die
> Tabelle unter v9 ungenutzt liegen und wird in Phase 4/6 entfernt (Schema 9.json wird neu generiert).
> `compileFdroidDebugKotlin` grün.

- `data/db/entity/ProfileEntity.kt`: Felder `deviceId: String`, `profileId: String`,
  `mergeTargetId: String?` ergänzen; `toDomain`/`fromDomain` erweitern.
- `domain/model/Profile.kt`: gleiche drei Felder; abgeleitete Properties `isOwner`
  (`profileId == deviceId`), `isMerged` (`mergeTargetId != null`).
- `data/db/AppDatabase.kt`: `version = 9`.
- **`device_profile_mappings` entfernen**: Entity, `DeviceProfileMappingDao`,
  `DeviceProfileMappingRepository(+Impl)`, Eintrag in `AppDatabase.entities`/abstract fun,
  DI in `di/AppContainer.kt`. (Konsumenten in Phase 4/6 mitumbauen — siehe dort.)
- Schema-Export: neues `app/schemas/…/9.json` wird beim Build erzeugt.

**Verify:** `compileFdroidDebugKotlin` (erwartete Folgefehler in noch nicht angefassten Konsumenten →
in den nächsten Phasen behoben; ggf. Phase 0+1+2 zusammen kompilieren).

---

## Phase 1 — Identitätserzeugung
**Ziel:** Profile bekommen echte (deviceId, profileId).

- `data/datastore/DeviceUuidProvider.kt`: bleibt — liefert `deviceId`.
- `data/repository/ProfileRepositoryImpl.kt`:
  - `createOwner`: `deviceId = profileId = deviceUuid`, `id = "$deviceUuid:$deviceUuid"`,
    `mergeTargetId = null`. Namens-Check bleibt nur „nicht leer".
  - `createProfile`: `profileId = UUID.randomUUID()`, `deviceId = deviceUuid`,
    `id = "$deviceId:$profileId"`. **`countByName`-Eindeutigkeitscheck entfernen.**
  - `updateName` / `updateOwnerName`: `countByName`-Check **entfernen**.
  - `generateProfileId(...)` löschen.
  - `existsByName`, `mergeProfiles`, `countCombinedGames` löschen (Merge → Phase 2).
- `domain/repository/ProfileRepository.kt`: Signaturen entsprechend anpassen
  (`existsByName`/`mergeProfiles`/`countCombinedGames` raus).
- `data/db/dao/ProfileDao.kt`: `countByName`, `deleteConflictingParticipants`,
  `reassignParticipants`, `reassignRoundResults`, `countCombinedGames` **entfernen**.

---

## Phase 2 — Merge als Zeiger (DAO + Repo)
**Ziel:** Set/Clear Merge-Target mit Kettenkollaps + Zyklenschutz.

- `data/db/dao/ProfileDao.kt`:
  - `@Query("UPDATE profile SET mergeTargetId = :target, updatedAt = :ts WHERE id = :id")` setMergeTarget
  - `@Query("UPDATE profile SET mergeTargetId = NULL, updatedAt = :ts WHERE id = :id")` clearMergeTarget
  - `@Query("UPDATE profile SET mergeTargetId = :newTarget, updatedAt = :ts WHERE mergeTargetId = :oldId")`
    repointPointers (Ketten nachziehen)
  - `observeOwner` (`profileId == deviceId`), `observeActiveSelectable`
    (`isArchived=0 AND mergeTargetId IS NULL AND profileId != deviceId`),
    `observeMerged` (`mergeTargetId IS NOT NULL`), `observeArchived` bleibt.
- `data/repository/ProfileRepositoryImpl.kt`:
  - `setMergeTarget(id, rawTargetId)`:
    1. Guards: `id != target`, `id` ist nicht Owner, Zyklencheck (folge Target-Kette, darf nicht auf
       `id` zurückführen).
    2. **Kettenende** auflösen: solange Ziel selbst `mergeTargetId` hat, weiterfolgen (Tiefenlimit).
    3. Transaktion: `setMergeTarget(id, finalTarget)`; `repointPointers(oldId=id, newTarget=finalTarget)`;
       `touch(finalTarget)`.
  - `clearMergeTarget(id)` (Unmerge): `mergeTargetId = NULL`, `touch`.
  - Pure Helper `resolveCanonical(idToTarget: Map<String,String?>, id): String` (Ketten folgen,
    Tiefenlimit, gibt Endknoten) — wird in Phase 3 von Stats genutzt.

---

## Phase 3 — Stats-Kanonisierung
**Ziel:** Merged zählen unters Target, Archivierte zählen nirgends. `StatsCalculator` bleibt fast unverändert.

- `data/repository/StatsRepositoryImpl.kt` → `buildSnapshot`:
  - Alle Profile via `profileDao.getAll()`. Baue `idToTarget` + `archivedIds`.
  - `canonical(id) = resolveCanonical(...)`; gilt ein Knoten in der Kette als archiviert → Ergebnis
    wird **verworfen** (Helper liefert `null`).
  - `participantsRaw` → profileId auf canonical mappen, `null`/archiviert rausfiltern, dedupen.
  - `results` → Kopien mit `profileId = canonical`; Results mit `null`-canonical **droppen**.
  - `profilesById` nur mit kanonischen, nicht-merged, nicht-archivierten Profilen befüllen.
  - `computeFingerprint`: bleibt (profile count + `getMaxUpdatedAt`); Merge/Unmerge/Archive fassen
    `updatedAt` an → Cache invalidiert korrekt. (Sicherstellen, dass `archive`/`unarchive` `updatedAt`
    setzen — tun sie bereits.)
- `domain/stats/StatsCalculator.kt`: keine Änderung erwartet (arbeitet auf bereits-kanonisierten Ids).
  Falls ein Pfad doch roh `profilesById` voraussetzt → prüfen.

---

## Phase 4 — NewGame: Auswahl-Filter + P2P-Reconciliation vereinfachen
- `data/db/dao/ProfileDao.kt`: `getActiveByPrefix` / `searchByNamePrefix` um
  `AND mergeTargetId IS NULL` ergänzen (Archiv-Filter bleibt). → Merged & Archivierte fliegen aus
  Auswahl/Autocomplete.
- `ui/newgame/NewGameViewModel.kt`:
  - `addNewProfile`: `NAME_EXISTS`-Zweig entfernen (Dups erlaubt). `AddError.NAME_EXISTS` aus Enum raus
    (UI-Strings mitziehen).
  - **`resolveLocalProfile` / `mergeJoinedParticipants` vereinfachen:** fremdes Profil wird **unverändert
    übernommen** (id = `participant.profileId`, deviceId = `participant.originDeviceId`). Kein Remap auf
    lokale Id, keine Namens-Uniquifizierung, kein `DeviceProfileMapping`. Nur: Profilzeile sicherstellen
    (insert-if-missing aus `ParticipantInfo`), Roster-Row mit fremder id anlegen.
  - `seedFromPreviousGame` / `continueSession`: „remote-Sitz?" nicht mehr über `mappingRepo`, sondern
    über `profile.deviceId != ownerDeviceId` bestimmen.
  - `mappingRepo` aus Konstruktor/Factory entfernen.
- `di/AppContainer.kt`: Mapping-Repo-Verdrahtung raus; VM-Factory-Aufrufe anpassen.
- **P2P-Selbstsitz:** `data/p2p/SessionManager.kt` (~Z.544–548) `reconcileSelfSeat`-Aufruf +
  `mySeatCanonicalId`-Plumbing entfernen; `BackupRepositoryImpl.reconcileSelfSeat` löschen
  (Begründung: Client-eigener Sitz reist als `"$clientDev:$clientDev"` = lokale Owner-id → Merge-by-id
  trifft sich selbst, kein Reconcile nötig).
- **`ParticipantInfo`** (`domain/p2p/model/SyncMessage.kt`): prüfen, ob `profileId` (Surrogat) +
  `originDeviceId` reichen, um die Profilzeile zu rekonstruieren. Falls Spalten `deviceId`/`profileId`
  nötig sind → optionale Felder mit Default ergänzen (rückwärtskompatibel).

---

## Phase 5 — Profilverwaltung-UI (4 Sektionen)
- `ui/tabs/settings/ProfileManagementViewModel.kt`:
  - State: `owner: ProfileRow?`, `active`, `merged`, `archived` (aus den neuen DAO-Flows).
  - `ProfileRow` für Merged um `targetName: String?` erweitern (aufgelöst über Target-id).
  - Aktionen: `setMergeTarget(id, targetId)`, `unmerge(id)`; alte `merge(keepId,discardId)` +
    `loadCombinedGameCount` entfernen. `RenameResult.EXISTS` entfernen.
- `ui/tabs/settings/ProfileManagementScreen.kt`:
  - Vier Sektionen rendern. Zustandsabhängige Menüs:
    - Aktiv: Umbenennen / Farbe / Mergen / Archivieren.
    - Archiviert: Entarchivieren / Mergen.
    - Merged: nur Unmerge; Untertitel „verschmolzen in <targetName>".
    - Owner: nur Umbenennen / Farbe.
  - Merge-Target-Picker-Dialog: Liste = aktive, nicht-merged Profile.
- `res/values*/strings.xml`: neue Strings (Merged-Section-Header, „verschmolzen in %s", Unmerge);
  `profile_rename_error_exists` / `NAME_EXISTS`-Strings entfernen (oder verwaisen lassen).

---

## Phase 6 — Join-Screen: „Lokales Profil zuweisen"
- `ui/p2p/JoinSessionViewModel.kt`:
  - `mapDevice` → `assignMerge(incoming: ParticipantInfo, localProfileId: String)`:
    fremde Profilzeile sicherstellen (insert-if-missing aus `incoming`), dann
    `profileRepo.setMergeTarget(incoming.profileId, localProfileId)`.
  - `mappingRepo` aus Konstruktor/Factory entfernen. `localProfiles`-Flow filtert zusätzlich
    `!isMerged` (Auswahl = aktive, nicht-merged, **geräteunabhängig**).
- `ui/p2p/JoinSessionScreen.kt`: pro Roster-Profil ein „Zuweisen"-Button → Picker lokaler aktiver
  Profile → `assignMerge`. Bestehende Mapping-UI ersetzen.
- DI-Anpassung in `AppContainer.kt`.

---

## Phase 7 — Backup
- `domain/backup/BackupModels.kt`: `BackupProfile` + `deviceId`, `profileId`,
  `mergeTargetId: String? = null` (Defaults = rückwärtskompatibel). `CURRENT_BACKUP_SCHEMA_VERSION = 2`.
- `data/repository/BackupRepositoryImpl.kt`: `toBackup`/`toEntity` um neue Felder; Import-Profilschritt
  trägt `mergeTargetId` mit (skip-if-exists by id). **Keine Reconciliation, kein `reconcileSelfSeat`**
  (in Phase 4 entfernt). Dangling/Ketten-Targets: **nicht** normalisieren — Stats-Auflösung folgt
  Ketten defensiv und ignoriert unbekannte Targets (Annahme #3).
- `SyncMessage.FullGameState` nutzt `BackupProfile` → trägt neue Felder automatisch.

---

## Phase 8 — Tests & Abschluss-Build
- `androidTest/.../ProfileMergeTest.kt` neu: Zeiger-Merge (`setMergeTarget`), Auflösung in Stats,
  Kettenkollaps (A→B, dann B→C ⇒ A→C), Zyklenschutz (A→B, B→A abgelehnt), Archiviert-ausgeschlossen,
  Unmerge.
- Build:
  - `./gradlew.bat :app:compileFdroidDebugKotlin --console=plain`
  - `./gradlew.bat :app:assembleFdroidDebug --console=plain`
  - F-Droid-Check unverändert (keine neuen Deps).
- APK auf beide Testgeräte per adb installieren (gemäß Notiz „Install APK, but don't drive it"),
  dann Übergabe für manuellen Golden-Path-Test (Onboarding-Owner, Profil anlegen/mergen/unmergen,
  Stats-Auflösung, P2P-Join + Zuweisen, Backup-Export/Import).

---

## Risiken / Aufpasspunkte
- **`ParticipantInfo`-Wire-Format**: muss deviceId/profileId sauber rekonstruierbar machen (Phase 4).
- **Stats-Doppelsitz**: zwei gemergte Profile im selben Spiel → Summierung unters Target (gewollt).
- **Cache-Invalidierung**: Merge/Unmerge/Archive müssen `updatedAt` anfassen (Fingerprint).
- **Owner-Schutz**: Owner nie als merge-/archivierbar; darf aber Merge-**Ziel** sein.
- Reihenfolge Phase 0–2 ggf. zusammen kompilieren (Konstruktor-/Interface-Brüche).
