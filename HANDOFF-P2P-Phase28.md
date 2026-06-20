# Handoff — Phase 28 P2P-Sync — Stage A + Stage B device-VERIFIED

**Updated:** 2026-06-20 · **Branch:** `v1.3.0` · **Spec:** `specs/27-P2P-Sync.md` ("Phase 28")
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

- **Game-end flow rework** (2026-06-20, built + installed both phones, **device test pending**):
  closing now happens on the **first** button (RoundSummary "Spiel abschließen" → `completeGame` =
  `closeGame` + `closeSharedGame`), so clients reach the game-end screen *then*. The closed game-end
  view shows [Statistik] + **[Neues Spiel starten]** for host/solo (seeds the prev game's players +
  settings via `Routes.newGameRoute(seedGameId, continueSession)`; host also brings clients along via
  new `SyncMessage.NewGameSetup` → `NavSignal.OpenNewGameWait` → `NewGameWaitScreen`, then the existing
  `OpenRound` pulls everyone into capture). A joined phone (`isP2pClient`) keeps **[Zur Startseite]**.
  Seeding skips device-mapped profiles when continuing (they repopulate from the live
  `joinedParticipants`). New sealed `SyncMessage` subclass → no `SyncProtocol` change.

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

1. **Return to a running game from the tabs.** A client thrown into capture by the host has no obvious
   "back to the game" entry point if it navigates away. The session now survives tab-switching, so
   re-entering via Game tab → open game → "Runde fortsetzen" *should* resync — but there's no dedicated
   button and the path isn't polished. Add a clear "rejoin running round" affordance.
2. **Real auto-reconnect.** A genuine BT drop / app kill currently needs a manual QR re-scan (the host
   then catches the rejoiner up). Consider client-side reconnect to the known host MAC.
3. **End-of-game confirmation.** No "Spieldaten synchronisiert (N Runden)" toast on clients yet (data
   does sync). `ImportResult.roundsAdded`/`roundsUpdated` is available to surface it.
4. **Corrections to finished hands.** MVP never reassigns a `done` unit; there is no "re-scan / edit a
   submitted hand" across devices.
5. **Necromancer race.** Mittelfeld is assigned first to minimise it, but a Necromancer hand finished
   before the discard syncs can pick from a stale candidate list (host recompute is authoritative).
6. **Duplicate client profiles across sessions.** `resolveLocalProfile` now auto-suffixes names on
   collision (`"Name (2)"`); revisit whether name-based identity continuity is the right long-term model
   vs. an explicit device→person binding.

Open the Stage-B plan (`glistening-noodling-seal.md`) and the memory for the full design rationale.
