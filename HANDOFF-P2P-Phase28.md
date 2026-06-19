# Handoff — Phase 28 P2P-Sync (Stufe A) — device-VERIFIED working

**Updated:** 2026-06-19 · **Branch:** `v1.3.0` · **Spec:** `specs/27-P2P-Sync.md` ("Phase 28")
**Plan:** `C:\Users\basti\.claude\plans\please-prepare-a-plan-rustling-iverson.md` (Stufe B = §155-189 of that same file)
**Memory:** `…/memory/project_p2p_handshake_cdm.md` (load it; running fix log)

**Stufe A (session setup) is code-complete AND verified end-to-end on two real phones.** Stufe B is not started.

---

## 1. Status

- **Stufe A** (protocol, RFCOMM transport, QR/code handshake via CDM, session setup, "join adds a
  player" roster, host-side reconciliation, DB migration 7→8) — **working on-device** (2026-06-19).
  Builds both flavors, F-Droid check clean, Room schema `8.json` present.
- **Stufe B** (locking, live card sync, reconnect, end-of-game distribution) — NOT started.
  `SessionManager.routeMessage` is the dispatch hook.

### Verified happy path (Pixel 8 host + Redmi Note 9 Pro client)
Host: New game → "Für Beitritt öffnen" → accept discoverable → QR shown.
Client: Home → join → scan QR → pick host in CDM picker → "Beigetreten – warte auf den Host".
Host: client's owner profile appears as a 2nd player in the roster → "Spiel starten" enabled → game
starts with both. Re-joining a second game works (no stale session).

### Test devices
- **Pixel 8** (host) — BT name "Pixel 8", adb `38011FDJH000N5`.
- **Redmi Note 9 Pro** (client) — MIUI / Android 12 / API 31, adb `cef2e19b`. Legacy CDM
  `associate(request, callback, handler)` path (API < 33).

---

## 2. The decisive fixes (non-obvious — keep these)

1. **CompanionDeviceManager instead of MAC-in-QR.** Own BT MAC is unreadable since Android 6, so the
   QR carries `hostBluetoothName` and CDM's system picker resolves the host MAC (no
   `ACCESS_FINE_LOCATION`). CDM needs an **Activity** context + the `companion_device_setup`
   `<uses-feature>` + the host actually **discoverable** (classic-BT `ACTION_FOUND` scan).
2. **MIUI legacy-BLUETOOTH permission.** On MIUI/API 31 `socket.connect()` throws
   `SecurityException: lacks permission android.permission.BLUETOOTH` despite `BLUETOOTH_CONNECT`
   being granted. Fix: declare `BLUETOOTH`/`BLUETOOTH_ADMIN` with **no `maxSdkVersion` cap** (the spec
   capped them at 30). Normal/auto-granted perms, not location → still F-Droid-clean. **Contradicts the
   spec manifest snippet on purpose.**
3. **Sticky-session reset.** `SessionManager` is a process-wide singleton; stale `Hosting`/`Connected`
   state leaked into the next game. Fix: `p2p.close()` in `NewGameViewModel.init` (host) and
   `JoinSessionViewModel.init` (client).
4. Earlier crash fixes: CDM from Activity ctx (not app ctx); locale via `applyOverrideConfiguration`
   (not `createConfigurationContext`, which detached the Activity); `QrCodeHelper` decode hardening
   (clamp crop height, TRY_HARDER + inverted retry); runtime `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE`.

> **MIUI swallows app `Log.d`.** On-device diagnostics must use `Log.w` or higher.

---

## 3. "Join adds a player" model (user-chosen, replaces the spec's reconciliation flow)

A joining device contributes its own player to the host roster (rather than the host typing every
player and the client only mapping):
- Client sends its local owner profile in `SyncMessage.DeviceJoined(deviceId, deviceName, participant:
  ParticipantInfo?)` — the `participant` field is the addition.
- `SessionManager` exposes host-side `joinedParticipants: StateFlow<List<ParticipantInfo>>` (deduped by
  `originDeviceId`, cleared on `close()`).
- `NewGameViewModel.mergeJoinedParticipants` → `resolveLocalProfile()`: reuse the
  `device_profile_mappings` entry for the device if present, else `createProfile(name)` + `updateColor`
  + record mapping, so `gameRepo.startGame` gets a valid local profileId. Then `broadcastRoster()`.
- Client UI: post-join `JoinedContent` "Beigetreten – warte auf den Host" roster view (string
  `p2p_joined_waiting`). The old mapping-dropdown screen is removed. `connectToHost` now takes a
  `selfParticipant`. `JoinSessionViewModel.localProfiles`/`mapDevice`/`LocalProfile` are now unused.

> Stage-B note: joined players are materialised as fresh local host profiles backed by the mapping
> table; the end-of-game merge must reconcile identity through that mapping.

---

## 4. Build / install / logcat

`adb` full path (not on PATH): `C:\Users\basti\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

```powershell
./gradlew.bat :app:assembleFdroidDebug --console=plain 2>&1 | Select-Object -Last 8
& "...\adb.exe" -s cef2e19b install -r "app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"
& "...\adb.exe" -s 38011FDJH000N5 install -r "app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk"

# Logcat (do NOT pipe adb through 2>&1 — native-exe stderr wrapping)
& "...\adb.exe" -s cef2e19b logcat -c
& "...\adb.exe" -s cef2e19b logcat -d | Select-String -Pattern "realmscore|[Cc]ompanion|AndroidRuntime|associate" | Select-Object -Last 80
```
F-Droid check (must be empty): `./gradlew.bat :app:dependencies --configuration fdroidDebugRuntimeClasspath | Select-String "(gms|firebase|mlkit|google-services)"`

---

## 5. Key files
- Transport: `data/p2p/BluetoothRfcommManager.kt` (+ `P2PConnection.kt`), serviceUuid `7f3b2c10-9a4d-4e6f-b1a2-2c0f5e8a9d01`
- Handshake: `data/p2p/{HandshakeManager,QrCodeHelper,CompanionDeviceHelper}.kt`
- Session: `data/p2p/SessionManager.kt` impl of `domain/p2p/P2PSessionRepository.kt`; protocol `data/p2p/SyncProtocol.kt`, models `domain/p2p/model/*` (`SyncMessage.DeviceJoined` carries `participant`)
- DB: `data/db/entity/DeviceProfileMappingEntity.kt` + DAO; `AppDatabase` v8; `migration/Migrations.kt` `MIGRATION_7_8`
- UI host: `ui/p2p/HostJoinSection.kt` (in `ui/newgame/NewGameScreen.kt`), `ui/newgame/NewGameViewModel.kt` (`mergeJoinedParticipants`/`resolveLocalProfile`, needs `deviceProfileMappingRepository`)
- UI client: `ui/p2p/JoinSessionScreen.kt` (`JoinedContent`) + `JoinSessionViewModel.kt`, `ui/p2p/QrScannerView.kt`; Home "join" in `ui/tabs/home/HomeScreen.kt`; route `JOIN_SESSION`
- DI: `di/AppContainer.kt`
- Manifest: `BLUETOOTH`/`BLUETOOTH_ADMIN` (NO maxSdk cap), `BLUETOOTH_CONNECT` (neverForLocation), `BLUETOOTH_ADVERTISE`, `companion_device_setup` feature; **no `ACCESS_FINE_LOCATION`**

---

## 6. Next: Stufe B
Follow the plan §155-189 (B1 LockManager + lock UI, B2 live card sync, B3 single-game export + LWW
`mergeGame`, B4 heartbeat/reconnect, B5 end-of-game `FullGameState` distribution). Dispatch hook:
`SessionManager.routeMessage`.
