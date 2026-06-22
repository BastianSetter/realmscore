# Handoff — Phase 28 P2P-Sync — Stage A + Stage B device-VERIFIED

**Updated:** 2026-06-21 · **Branch:** `v1.3.0` · **Spec:** `specs/27-P2P-Sync.md` ("Phase 28")
**Plan:** `C:\Users\basti\.claude\plans\please-prepare-a-plan-rustling-iverson.md` (Stufe B = §155-189)
**Stage-B plan (this session):** `C:\Users\basti\.claude\plans\glistening-noodling-seal.md`
**Memory:** `…/memory/project_p2p_handshake_cdm.md` (load it; running fix log)

**Both stages work end-to-end on two real phones.** Stage A = session setup (committed `42a20f7`).
Stage B = multi-device live-capture (committed `244a4e5`, device-fixes `7c9f84a`).

---

## 1. Status

- **Stage A** (RFCOMM transport, QR/code handshake via CompanionDeviceManager, "join adds a player"
  roster, host reconciliation, DB migration 7→8) — working on-device.
- **Stage B** (game-start distribution, distributed work-stealing capture, host-authoritative locking,
  live card sync, pre-reveal waiting screen, host reveal, heartbeat + reconnect, end-of-game
  distribution + self-seat reconcile) — **working on-device 2026-06-20.** Builds both flavors,
  F-Droid check clean, DB still **v8** (no new entities — only query-only DAO additions).

- **Adaptive multi-phone assign order + join fix** (2026-06-20, **device-VERIFIED on 2 phones**): from
  round 2 on, each phone's auto-assign priority is *its own previous-round submit list first*, then a
  shared round-robin "combined" list (index 0 of every device, then index 1, … — devices ordered by id)
  **walked in reverse** (the front of the combined list is everyone's own seat, re-grabbed via the
  own-list priority, so a stealing phone takes the leftover later-position hands from the back first —
  matches the spec "C gets Hand 3, the last one", and the on-device test `A:{1,3,4} B:{2}` → B steals
  `4` then `3`). Round 1 keeps the seat-based order. The **host** records the per-device submit order in
  memory (`submissionLog`, attributed via the lock holder at `markDone`), snapshots the finished round's
  log into `SyncMessage.StartRound.priorSubmissions` + `P2PSessionRepository.roundOrderSeed` when the
  next round opens, and every phone builds its order locally via the pure `domain/game/DistributedAssignOrder`
  (unit-tested). In-memory only (host app-kill mid-game → next round falls back to seat order). No DB
  change (v8). Single-phone ordering (`CaptureOrdering` / `lastScanOrder`) was already correct and is
  untouched.
  - **Join bug fixed (was blocking the test):** a locally added player (`addExistingProfile`) used to
    copy the *added profile's* `originDeviceId` into its roster row. A profile synced in from another
    device carries that device's id, so adding it made the local row claim a remote device — and since
    `mergeJoinedParticipants` dedups joiners by `originDeviceId`, the real device's join was dropped as
    "already known". Fix: a locally added player is captured by the host, so its row now carries the
    host's own device id (`hostDeviceId`, the local owner's `originDeviceId`). Also added
    `P2PSessionRepository.resetJoinedRoster()` (clears stale joiners on a fresh new game without
    dropping live connections). Rule: a roster row's `originDeviceId` = "device that captures this seat"
    (host for owner/locals, remote only for true joins), never "device the profile was created on".

- **Silent rejoin after app-kill / BT-drop + host QR re-display** (2026-06-21, **device-VERIFIED on 2
  phones**): see §6 #2 — the client reconnects to the persisted host MAC with no QR re-scan; the host can
  re-show its QR mid-game as the fallback. New: `data/p2p/LastSessionStore.kt`, `domain/p2p/model/RejoinInfo.kt`,
  `ui/p2p/HostQrDialog.kt`; tri-state Home card in `HomeViewModel`/`HomeScreen`. DataStore only, DB v8, F-Droid clean.

- **Corrections to finished hands (§6 #4)** (2026-06-21, **device-VERIFIED on 2 phones**): any device can
  re-open and fix a submitted hand (un-revealed *and* post-reveal), the fix propagates to every mirror and
  rescores; corrected card availability is live, and the round summary refreshes in place. Also fixed the
  duplicate-`round_result` merge bug (was double-counting + retaining freed cards) and added a stale-rejoin
  escape hatch. See §6 #4 for the full breakdown. DB still v8, F-Droid clean.

- **Game-end flow rework** (2026-06-20, built + installed both phones, **device test pending**):
  closing now happens on the **first** button (RoundSummary "Spiel abschließen" → `completeGame` =
  `closeGame` + `closeSharedGame`), so clients reach the game-end screen *then*. The closed game-end
  view shows [Statistik] + **[Neues Spiel starten]** for host/solo (seeds the prev game's players +
  settings via `Routes.newGameRoute(seedGameId, continueSession)`; host also brings clients along via
  new `SyncMessage.NewGameSetup` → `NavSignal.OpenNewGameWait` → `NewGameWaitScreen`, then the existing
  `OpenRound` pulls everyone into capture).
  Seeding skips device-mapped profiles when continuing (they repopulate from the live
  `joinedParticipants`). New sealed `SyncMessage` subclass → no `SyncProtocol` change.

- **"Zurück zum Hauptmenü" on the game-end screen** (2026-06-22, device-VERIFIED both phones): every
  device (host, joined-phone, solo) gets a **[Zurück zum Hauptmenü]** button — host/solo as a secondary
  `OutlinedButton` under [Neues Spiel starten], the joined-phone as its only action (replacing the old
  [Zur Startseite]). It routes through new `GameSummaryViewModel.leaveToMenu`, which calls `p2p.close()`
  (tears down all sockets → `SessionState.Idle`) before the existing `onCloseGameDone` nav (Home +
  `clearBackStack(SECTION_GAME)`), so the user returns to a fresh, connection-free cold-start state.
  Safe for all roles: the game is already closed here, so the client's `rejoinInfo` was wiped by
  `GameClosed`; solo `close()` is a no-op. Fixes the "stuck on game-end screen" feel after a P2P game.

### Verified on device (Pixel 8 host `38011FDJH000N5`, Redmi `cef2e19b`)
Both phones start the game together; each captures its own hand then auto-steals the next free
unit; used cards entered on one phone are excluded on the other; the early-finisher gets the waiting
screen with "Übernehmen"; the host computes the reveal and everyone opens it; disconnect→reconnect
catches a rejoining client up; the host closing the game throws all devices to the game-end screen and
each device's copy shows under its own owner profile.

---

## 2. Architecture (Stage B)

**Identity:** canonical = **host profile UUIDs** everywhere during the live session and in every
device's mirror. Each client's own seat is reattributed to its local owner profile **only at game end**
(`BackupRepository.reconcileSelfSeat`). Keeps locking/sync trivial (pure shared UUIDs).

**Data:** each phone keeps its own Room copy (the "mirror"). `BackupRepository.exportGame` /
`mergeGame` (LWW on `updatedAt`) distribute and fold a single game; `GameMirrorSync` applies inbound
`FullGameState` / `HandCardUpdate` / `DiscardUpdate` to the local mirror (recomputing scores —
per-hand deterministic).

**Coordination:** `SessionManager` is the hub. Host holds the authoritative `LockManager`; clients
send `LockRequest/LockRelease/UnlockRequest/UnitDone` and the host re-broadcasts `RoundStatus` (locks +
done). Host relays `HandCardUpdate/DiscardUpdate` to the other clients (`broadcastExcept`). 5 s Ping /
30 s timeout heartbeat → on a drop the host auto-reclaims the dead device's locks; `onClientConnected`
sends a catch-up (`FullGameState` + `RoundStatus` + `StartRound`) so a re-scanning client resyncs.

**Auto-assign** lives in `RoundCaptureViewModel`: own unit first (host takes Mittelfeld #0 first for
Necromancer correctness), then `[Mittelfeld, hands by seatOrder]`; optimistic lock display; finishing
with units left → `waitingForOthers` (inline waiting screen).

**Navigation:** one central `navSignals` observer in `MainScaffold` follows the host —
`OpenRound` (clients into each round), `OpenReveal` (all into the reveal), `OpenGameSummary` (all to
game end). The host navigates itself via the new-game / next-round callbacks (it doesn't signal itself
for rounds, only for reveal/end). Reveal→summary is local per device.

---

## 3. Non-obvious fixes — KEEP THESE

**Stage A (still apply):**
1. **CompanionDeviceManager instead of MAC-in-QR** (own BT MAC unreadable since Android 6). QR carries
   `hostBluetoothName`; CDM resolves the MAC (no `ACCESS_FINE_LOCATION`). Needs an **Activity** context.
2. **MIUI legacy-BLUETOOTH permission:** declare `BLUETOOTH`/`BLUETOOTH_ADMIN` with **no `maxSdkVersion`
   cap** (contradicts the spec snippet on purpose) or MIUI throws `SecurityException` on `socket.connect()`.
3. **MIUI swallows `Log.d`** — use `Log.w`+ for on-device diagnostics.

**Stage B (this session):**
4. **Roster keys must be `profileId`, not `originDeviceId`** — several players added on one device share
   an `originDeviceId` → duplicate-key `LazyColumn` crash. Rosters are also deduped by `profileId` on
   broadcast + receive.
5. **`resolveLocalProfile` must never throw and never reuse a seated id.** `createProfile` rejects
   duplicate names, so a joiner whose name already exists used to throw → client unseated → "Spiel
   starten" silently no-op (the start path swallows the exception). It now reuses mapping → reuses an
   exact-name profile → else creates an auto-suffixed name; never returns an already-seated id (else
   `startGame`'s distinct check throws).
6. **Don't `close()` the session on NewGame/Join `init` while a game is live** — that Stage-A reset
   dropped everyone mid-game. Guarded by `isInActiveGame()` (tracked on host *and* client via
   `StartRound`/`GameClosed`). Explicit `openForJoins`/`connectToHost` still reset a prior session.
7. **Per-round status is keyed by bare unitId (profileId)** — filter a stale previous-round
   `RoundStatus` by `roundId` before seeding a new round's VM, or its `doneUnitIds` look done.

---

## 4. Build / install / logcat

`adb`: `C:\Users\basti\AppData\Local\Android\Sdk\platform-tools\adb.exe` (not on PATH).

```powershell
./gradlew.bat :app:compileFdroidDebugKotlin --console=plain 2>&1 | Select-Object -Last 40   # fast check
./gradlew.bat :app:assembleFdroidDebug --console=plain 2>&1 | Select-Object -Last 8          # APK
& "...\adb.exe" -s 38011FDJH000N5 install -r "app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"
& "...\adb.exe" -s cef2e19b install -r "app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"

# Crash buffer (do NOT pipe adb through 2>&1):
& "...\adb.exe" -s cef2e19b logcat -b crash -d | Select-Object -Last 80
```
F-Droid check (must be empty): `./gradlew.bat :app:dependencies --configuration fdroidDebugRuntimeClasspath | Select-String "(gms|firebase|mlkit|google-services)"`

---

## 5. Key files

- Protocol/models: `domain/p2p/model/SyncMessage.kt` (`StartRound`, `RoundStatus`, `UnitDone`,
  top-level `UnitLock`), `domain/p2p/model/NavSignal.kt`
- Session hub: `data/p2p/SessionManager.kt` (impl of `domain/p2p/P2PSessionRepository.kt`),
  `data/p2p/LockManager.kt`, `data/p2p/GameMirrorSync.kt`
- Backup/merge: `domain/repository/BackupRepository.kt` + `data/repository/BackupRepositoryImpl.kt`
  (`exportGame`/`mergeGame`/`reconcileSelfSeat`), `domain/backup/BackupModels.kt` (`GameSnapshot`)
- Capture: `ui/game/RoundCaptureViewModel.kt` + `RoundCaptureScreen.kt` (auto-assign, lock UI, waiting)
- Roster/host: `ui/newgame/NewGameViewModel.kt` (`resolveLocalProfile`, start branch)
- Client/nav: `ui/p2p/JoinSessionViewModel.kt` + `JoinSessionScreen.kt`, `ui/nav/MainScaffold.kt`
  (central nav observer); next-round/game-end: `ui/reveal/RoundSummaryViewModel.kt`,
  `ui/summary/GameSummaryViewModel.kt`
- DI: `di/AppContainer.kt`

---

## 6. Next: improve the multiplayer flow (not yet done)

These are known gaps / good next steps for a fresh session:

1. ~~Return to a running game from the tabs.~~ **Decided unnecessary (2026-06-21):** while the app
   stays open you can't lose the connection to the active round — you just navigate back via the Game
   tab, which holds the live session. The only real loss-of-connection case is app close / BT drop,
   covered by #2 below. (An earlier "Laufende Runde fortsetzen" Home button was built and reverted.)
2. **Real auto-reconnect — DONE + device-VERIFIED 2026-06-21.** A client whose app was killed or whose
   Bluetooth dropped silently reconnects to the last host (no QR re-scan). The CDM-resolved host MAC +
   handshake payload are persisted in a new `data/p2p/LastSessionStore.kt` (DataStore `p2p_session_prefs`),
   written on a successful `connectToHost`, **loaded on `SessionManager` init**, and **cleared on
   `GameClosed`** (NOT on `close()`, which fires on every new-game/join visit). Exposed as
   `P2PSessionRepository.rejoinInfo: StateFlow<RejoinInfo?>`. The Home "session" card is now tri-state
   (`HomeViewModel.P2pCardState`): **Hosting** → "Session-QR anzeigen" (re-shows the QR live from
   `SessionState.Hosting` via the new `ui/p2p/HostQrDialog.kt`, the re-scan fallback); **client with a
   stored host** → "Session erneut beitreten" → the join screen auto-fires `vm.rejoin()` which **reuses
   `connectToHost`** with the stored MAC+payload (no CDM, no QR), and on failure shows the normal scanner;
   **idle** → "Session beitreten". Direct RFCOMM reconnect to the stored MAC works without re-running CDM
   (the association persists; `connect()` needs only `BLUETOOTH_CONNECT`). No new protocol message, no DB
   change (DataStore only, v8). F-Droid clean.
3. ~~End-of-game confirmation.~~ **Dropped (2026-06-21):** the end-game data sync is already silent and
   complete (B7 — each client merges the full game and shows it under its own owner, device-verified).
   #3 only ever proposed *adding* a "Spieldaten synchronisiert (N Runden)" toast on top; the user wants
   it to stay silent, so there is nothing to do.
4. **Corrections to finished hands — DONE + device-VERIFIED 2026-06-21** (any-device, reactive). Any
   device may re-open a finished hand and fix it; the fix relocks, re-captures, re-pushes into every
   mirror and rescores. Core primitive: `LockManager.reopen` (un-done + lock to requester) driven by a
   new auto-registered `SyncMessage.ReopenUnit` (no `SyncProtocol` change) via
   `P2PSessionRepository.reopenUnit`. **Un-revealed window:** `RoundCaptureViewModel.switchToPlayer`
   now routes by lock state — own→select, free→grab, **done→re-open for correction**, locked-by-other→
   ignored (folds in the "tap any open hand" dropdown); `pendingSwitchUnit` honours the explicit target
   once the host grants it and reloads the draft from the mirror. **Post-reveal:** re-entering a
   completed round (`round.completedAt != null`) sets `isEditingCompletedRound` → the host's auto-reveal
   is suppressed and `WaitingForOthersContent` shows an "Runde bearbeiten" list with **Korrigieren** per
   hand + a **Fertig** button (`onDoneEditing` → back to the summary). `RoundSummaryViewModel` now
   recomputes scores+order **reactively** from `observeResultsForGame`, so a correction refreshes every
   device's summary *in place* (no re-reveal animation). Card availability is **live during a
   correction**: `RoundCaptureViewModel.buildCurrent` skips a unit's stale mirror copy when it has a
   live draft, so a swapped card frees on the other phone the moment it's picked (not on submit).
   - **Root-cause fix that actually unblocked it:** `mergeGame` reconciled `round_result`s **by result
     id**, but every device mints its own UUID in `HandCardRepositoryImpl.saveHand`, so each
     `FullGameState` merge (e.g. at a reveal) inserted a **duplicate** `round_result` per seat —
     `observeHandCardKeysByProfile` then reported both the old and new card (and stats double-counted
     scores). `BackupRepositoryImpl.mergeExistingRound` now reconciles **by (round, profile)**,
     overwriting the existing row in place. Pre-fix games may still hold duplicate rows; test fresh.
   - **Stale-rejoin escape hatch (separate bug surfaced while testing):** `rejoinInfo` was only cleared
     on `GameClosed`, so an abandoned/killed game left the client pinned to the old host with no way to
     scan a different session. Added `P2PSessionRepository.clearRejoinInfo()` + a "Andere Session
     beitreten (QR scannen)" button on the join screen (`JoinSessionViewModel.forgetLastSession`).
   - Files: `LockManager`, `SessionManager`, `SyncMessage`, `P2PSessionRepository`,
     `RoundCaptureViewModel`/`Screen`, `RoundSummaryViewModel`, `MainScaffold`,
     `JoinSession{ViewModel,Screen}`, `BackupRepositoryImpl`, strings. DB still **v8**, F-Droid clean.
5. **Necromancer pick logic — REWORKED 2026-06-22 (built + installed both phones, device test pending).**
   The earlier "host recompute is authoritative" note was wrong: scoring is deterministic from
   `(hand, jokerAssignments)` and never reads the discard pile, so the host re-score (`GameMirrorSync`)
   faithfully reproduces whatever pull the client chose — it neither re-validates nor re-optimises it.
   The pull is now gated by a single rule, **identical single-phone and P2P** (a client whose Mittelfeld
   hasn't synced simply has `discardScanned = false`, so it lands on the un-scanned path automatically):
   - **Candidate pool (manual pick):** excludes every card held in *any* hand (own + others — a held
     card can't be in the discard). The Mittelfeld is **never** an exclusion: scanned → it IS the pool
     (`getNecromancerCandidates` filters to the discard keys = *selection*); un-scanned → it plays no part
     at all (neither the in-progress draft nor the persisted pile leaks in). New VM helper
     `RoundCaptureViewModel.cardsInHands()` (all seats, never `DISCARD_ID`); `necromancerCandidates()` is
     now parameter-less. Distinct from `buildCurrent`'s per-hand `usedByOthers`, which *does* include the
     Mittelfeld (the normal card-picker / camera-scan must keep discard cards out of a hand) — left
     untouched, as it feeds the device-verified live-availability sync.
   - **Optimiser gating** (`JokerSection`, state-driven via new `PlayerHandEntryUiState.mittelfeldScanned`):
     Necromancer in hand + Mittelfeld un-scanned + no pick → optimiser **disabled** with the hint
     *"Mittelfeld nicht gescannt. Bitte setze den Totenbeschwörer erst manuell."*; once a pick is set the
     button **enables** and the hint becomes *"… Totenbeschwörer wird nicht optimiert."* (it optimises the
     other jokers, carries the pull through). Scanned Mittelfeld (or no Necromancer) → unchanged; the
     optimiser brute-forces the pull from the discard. Defaults keep the sandbox / multi-hand tools as-is.
   - Files: `RoundCaptureViewModel`, `PlayerHandEntryViewModel` (state field), `JokerSection`,
     `PlayerHandCaptureContent`, `PlayerHandEntryScreen`, strings. No DB / protocol change.
6. ~~Duplicate client profiles across sessions.~~ **Resolved by the Profil-Rework (`c56cbb8`), 2026-06-22.**
   The old name-based remap (`resolveLocalProfile` + auto-suffix `"Name (2)"`) is gone. Identity is now a
   globally unique `(deviceId, profileId)`; the host takes a joined foreign profile **as-is** via the
   idempotent `ProfileRepository.ensureRemoteProfile` (`dao.getById(id)` short-circuit), so the same person
   re-joining from the same device (stable owner id `profileId == deviceId`) never spawns a new duplicate
   across sessions. The design question (name-based continuity vs. explicit device→person binding) is
   decided in favour of an explicit, **non-destructive, persisted, reversible** binding: the Join screen's
   per-profile **Zuweisen** button (`JoinSessionViewModel.assignMerge` → `setMergeTarget`) points an
   incoming profile at a chosen local one; the pointer survives re-joins, so you bind once and every later
   session auto-resolves it. Residual (narrow, non-P2P): name uniqueness is generally relaxed, so a host
   re-creating the *same local manual* profile (e.g. a deviceless "Oma") twice now silently makes two rows
   — a normal profile-management concern, cleaned up via the merge UI, not an identity-design gap. Optional
   future UX nicety: proactively suggest `assignMerge` when an incoming `name` matches an active local
   profile (currently the button is purely manual).

Open the Stage-B plan (`glistening-noodling-seal.md`) and the memory for the full design rationale.
