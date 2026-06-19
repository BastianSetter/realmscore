# Phase 28 – P2P-Sync (NFC/QR + Bluetooth)

> **Umsetzungsplan:** `C:\Users\basti\.claude\plans\please-prepare-a-plan-rustling-iverson.md`
> (lokale Umsetzung auf Branch `v1.3.0`; QR + 6-stelliger Code, NFC zurückgestellt; ZXing-Core).

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Backup/Restore (Phase 23, `domain/backup/BackupModels.kt` + `BackupRepositoryImpl`)
ist vorhanden – diese Phase nutzt dieselben DTOs und dieselbe Merge-Logik. **Dies ist die technisch
komplexeste Phase. Sorgfältig lesen und bei Unklarheiten vor der Implementierung nachfragen.**

> **Post-Release-Arbeit ("Phase 2" laut `00-vision.md`).** Reihenfolge im Repo: 26 (Kamera),
> 28 (P2P), 30 (Erweiterung). Vor Beginn klären, ob der Aufwand (zwei reale Geräte zum Testen,
> NFC/Bluetooth-Sonderfälle) im Verhältnis zum Nutzen steht.

---

## Stand der Codebasis (Juni 2026 – bei Erstellung dieser Revision)

- Paketname: `de.morzo.realmscore`.
- **`kotlinx.serialization` ist inzwischen aktiv genutzt** (nicht mehr „im Catalog, ungenutzt"):
  `domain/backup/BackupModels.kt` ist komplett `@Serializable`. Das Sync-Protokoll kann darauf aufbauen.
- **Backup-DTOs existieren bereits** und sind die richtige Basis für die Datenverteilung am Spielende:
  `BackupData`, `BackupGame`, `BackupRound`, `BackupResult`, `BackupHandCard`, `BackupDiscardCard`,
  `BackupProfile` (+ `CURRENT_BACKUP_SCHEMA_VERSION = 1`). **Es gibt keinen Typ `GameBackupData`** –
  unten wird `BackupGame` verwendet.
- **Merge-Logik existiert bereits** in `data/repository/BackupRepositoryImpl.kt` (Import = Merge-by-UUID
  auf **Runden-Granularität**, Ergebnis als `ImportResult`). Die Datenverteilung am Spielende soll diese
  Logik wiederverwenden, nicht neu erfinden.
- **Device-ID:** `data/datastore/DeviceUuidProvider.kt` liefert via `get()` eine persistente
  Geräte-UUID (DataStore „device_prefs"). Profile tragen `originDeviceId`. Beides als `deviceId` nutzen.
- **Room-DB:** `AppDatabase` ist aktuell **Version 7**, Migrationen in `data/db/migration/Migrations.kt`.
  Die neue Tabelle `device_profile_mappings` braucht **Migration 7 → 8** plus Eintrag in `AppDatabase`.
- **DI:** Alles hängt an `di/AppContainer.kt` (lazy Properties, manuelle DI, kein Hilt). Die neuen
  P2P-Manager dort halten.
- Spielanlegen: `ui/...` NewGame-Flow + `GameRepository`; Home-Tab über `ui/nav/MainScaffold.kt`.

---

## Kontext (kurz)

Mehrere Spieler am selben Tisch können über P2P eine gemeinsame Spielsession teilen. Jeder tippt seine eigenen Karten ein, alle sehen denselben Stand. Profile werden zwischen Geräten abgeglichen (Merge-Logik). Am Ende hat jedes Gerät das vollständige Spiel lokal gespeichert.

**Technische Entscheidung:** Host-basiert. Das Gerät das das Spiel gestartet hat ist der Host. Andere Geräte verbinden sich als Clients. Einfacher als Mesh, für diesen Use Case ausreichend.

**Protokoll:** Handshake (Geräte-/Verbindungsinfos austauschen), dann Bluetooth Classic RFCOMM für die laufende Session. Siehe Handshake-Abschnitt für die wichtige Korrektur zum NFC-Mechanismus.

---

## Scope

### Drin
- Session-Setup via Handshake (Host & Client)
- Bluetooth-RFCOMM-Verbindung für Live-Daten
- Live-Sync der Spielerliste beim Spielerstellen
- Optimistic Locking für Karteneingaben
- Profile-Reconciliation (lokales Profil ↔ fremde Device-ID), persistent
- Resilientes Offline-Weiter bei Verbindungsabbruch + Sync beim Reconnect
- Vollständige Datenverteilung am Spielende auf alle Geräte (über die Backup-Merge-Logik)

### Explizit NICHT drin
- Kein Cloud-Relay (alles lokal)
- Kein permanenter Hintergrund-Sync (nur während einer aktiven Session)
- Keine Unterstützung für mehr als 6 Geräte gleichzeitig (max 6 Spieler)

---

## Was am Ende funktionieren muss

### Session-Setup
1. Spieler A erstellt Spiel → Button „Für Beitritt öffnen" erscheint
2. Spieler B öffnet App → „Session beitreten"-Button im Home-Tab
3. Handshake (NFC/QR/Code, s. u.) überträgt Host-Verbindungsinfos an B
4. B sieht die Spielerliste von A in Echtzeit
5. A tippt „Maria" → erscheint bei B sofort
6. B kann für sich „Lokales Profil zuordnen" („Maria auf Gerät B ist mein Profil Maria")
7. A und B starten das Spiel gemeinsam

### Während des Spiels
8. B beginnt „Karten für Profil XY eingeben" → bei A als „geblockt (B)" markiert
9. B gibt Karten ein → live zu A synchronisiert
10. Verbindungsabbruch: beide Geräte speichern lokal weiter
11. Reconnect: Delta-Sync (nur Änderungen seit Trennung)

### Spielende
12. Alle Geräte haben vollständige Spieldaten lokal

---

## Tech-Stack (F-Droid-konform, keine Location-Permission)

**Protokoll-Wahl:** Bluetooth Classic RFCOMM für die laufende Session.

**Warum nicht WiFi-Direct?** WiFi-Direct benötigt ab API 29 `ACCESS_FINE_LOCATION` für die Peer-Discovery-Phase. Das wollen wir vermeiden.

**Warum Bluetooth ohne Location?** Location wird nur für Bluetooth-*Discovery* (unbekannte Geräte finden) benötigt. Da wir die Bluetooth-MAC-Adresse über den Handshake übertragen, brauchen wir keine Discovery → keine Location-Permission.

```kotlin
// libs.versions.toml – KEINE neuen Libraries nötig
// Bluetooth Classic RFCOMM ist Teil des Android SDK
// kotlinx.serialization ist bereits im Projekt aktiv (Backup-Format)
// QR-Fallback: ein F-Droid-taugliches QR-Lib prüfen (z. B. ZXing-Core, KEIN ML Kit)
```

### Benötigte Permissions (minimal!)

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.NFC" />

<!-- Bluetooth API 26-30 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- Bluetooth API 31+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Für den QR-Fallback (falls genutzt): -->
<uses-permission android:name="android.permission.CAMERA" />

<uses-feature android:name="android.hardware.nfc" android:required="false" />
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
```

**Kein `ACCESS_FINE_LOCATION`!** Der Verzicht auf Discovery macht das möglich.

---

## Architektur

```kotlin
// data/p2p/
├── HandshakeManager.kt         // Verbindungsinfos austauschen (NFC-Reader-Mode / QR / Code)
├── BluetoothRfcommManager.kt   // Bluetooth RFCOMM: Verbindungsaufbau + Datentransfer
├── SessionManager.kt           // High-Level: Session-State, Clients verwalten
├── SyncProtocol.kt             // Nachrichten-Format (kotlinx.serialization)
└── LockManager.kt              // Optimistic Locking

// domain/p2p/
├── P2PSessionRepository.kt     // Interface
└── model/
    ├── SessionState.kt
    ├── SyncMessage.kt
    └── DeviceLock.kt

// data/db/entity/DeviceProfileMappingEntity.kt + DAO + Migration 7→8
```

Manager als lazy Properties in `di/AppContainer.kt` registrieren.

---

## Handshake — ⚠️ wichtige Korrektur gegenüber der Erstfassung

Die Erstfassung nutzte **`NfcAdapter.setNdefPushMessage()` (Android Beam)**. **Android Beam wurde mit
Android 10 (API 29) entfernt** und ist auf den hier relevanten Geräten (minSdk 26, targetSdk 36) nicht
mehr verfügbar. `setNdefPushMessage`/`setNdefPushMessageCallback` sind deprecated und auf API 29+ ohne
Wirkung. **Dieser Mechanismus existiert nicht mehr – er muss ersetzt werden.**

Auszutauschende Verbindungsinfo bleibt gleich:

```kotlin
@Serializable
data class HandshakePayload(
    val hostDeviceId: String,
    val bluetoothMacAddress: String,  // z.B. "AA:BB:CC:DD:EE:FF"
    val gameId: String,
    val sessionToken: String          // UUID zur Verifikation
)
```

**Empfohlener Weg (in dieser Reihenfolge):**

1. **QR-Code als primärer Pfad (robust, keine NFC-Hardware nötig).** Host zeigt einen QR-Code mit der
   JSON-`HandshakePayload`; Client scannt ihn (Kamera). Funktioniert auf allen Geräten und ist gut testbar.
2. **6-stelliger Session-Code** als manuelle Alternative (z. B. wenn keine Kamera).
3. **NFC nur, falls explizit gewünscht, dann via Reader-Mode / NDEF-Tag-Lesen** (nicht Beam):
   Host emuliert ein NDEF-Tag über **HCE (Host Card Emulation)** oder die Payload wird auf ein
   physisches Tag geschrieben; Client liest im **`NfcAdapter.enableReaderMode()`**. Das ist deutlich
   aufwändiger als Beam früher – im Zweifel zuerst nur QR + Code umsetzen und NFC zurückstellen.

Ablauf nach erhaltener Payload (egal über welchen Kanal): Client verbindet sich **direkt** per Bluetooth
an die bekannte MAC-Adresse (**kein Scan, keine Discovery**).

---

## Bluetooth RFCOMM Verbindung

```kotlin
class BluetoothRfcommManager(private val context: Context) {
    // BluetoothAdapter.getDefaultAdapter() ist deprecated → über BluetoothManager holen:
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    // Echter, fixer UUID-Wert (NICHT der Paketname – "de.morzo.realmscore.p2p" ist KEIN gültiger UUID!).
    // Einmal generieren und konstant halten, beide Seiten müssen denselben Wert nutzen:
    private val serviceUuid = UUID.fromString("7f3b2c10-9a4d-4e6f-b1a2-realmscore01")  // Platzhalter: gültige UUID generieren

    // Host: wartet auf eingehende Verbindungen
    suspend fun startServer(): BluetoothServerSocket =
        adapter!!.listenUsingRfcommWithServiceRecord("RealmScore", serviceUuid)

    // Client: verbindet sich an bekannte MAC (kein Discovery nötig!)
    suspend fun connectToHost(macAddress: String): BluetoothSocket {
        val device = adapter!!.getRemoteDevice(macAddress)  // direkt per MAC, kein Scan
        return device.createRfcommSocketToServiceRecord(serviceUuid).also { it.connect() }
    }
}

interface P2PConnection {
    suspend fun send(message: SyncMessage)
    fun receive(): Flow<SyncMessage>
    fun isConnected(): Boolean
    fun close()
}
```

> Auf API 31+ benötigen `getRemoteDevice`/`connect`/`listen…` die Laufzeit-Permission
> **`BLUETOOTH_CONNECT`** – vor dem Verbindungsaufbau abfragen.

**Transport-Layer:** `BluetoothSocket` InputStream/OutputStream mit zeilengetrennten JSON-Strings. Pro Nachricht: `${json}\n`.

**Warum kein Location?**
- `adapter.getRemoteDevice(macAddress)` ist eine reine Lookup-Operation – kein Scan
- `createRfcommSocketToServiceRecord` + `connect()` verbindet direkt – kein Scan
- Nur `startDiscovery()` würde Location brauchen – das rufen wir nie auf

---

## Sync-Protokoll

```kotlin
@Serializable
sealed class SyncMessage {
    @Serializable data class PlayerListUpdate(val participants: List<ParticipantInfo>) : SyncMessage()
    @Serializable data class LockRequest(val profileId: String, val roundId: String, val deviceId: String) : SyncMessage()
    @Serializable data class LockRelease(val profileId: String, val roundId: String) : SyncMessage()
    @Serializable data class HandCardUpdate(val roundId: String, val profileId: String, val cards: List<HandCardSyncData>) : SyncMessage()
    @Serializable data class DiscardUpdate(val roundId: String, val cards: List<String>) : SyncMessage()
    @Serializable data class RoundComplete(val roundId: String) : SyncMessage()
    @Serializable data class GameClosed(val gameId: String, val closedAt: Long) : SyncMessage()
    @Serializable data class FullGameState(val game: BackupGame) : SyncMessage()  // beim Reconnect/Spielende; Typ aus BackupModels.kt
    @Serializable data class UnlockRequest(val profileId: String, val roundId: String) : SyncMessage()  // forciertes Entsperren
    @Serializable data class DeviceJoined(val deviceId: String, val deviceName: String) : SyncMessage()
    // Parameterlose Nachrichten sind OBJECTS, keine data class (data class ohne Felder ist ungültig):
    @Serializable object Ping : SyncMessage()
    @Serializable object Pong : SyncMessage()
}
```

> `FullGameState` trägt einen **`BackupGame`** (aus `domain/backup/BackupModels.kt`). So lässt sich der
> Stand mit derselben Serialisierung und derselben Merge-Logik wie das Backup verteilen.

---

## Session-Setup UI

### Host (NewGame-Flow)
```kotlin
// Wenn Spiel angelegt:
OutlinedButton(onClick = { sessionManager.openForJoins(game) }, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.Share, null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.p2p_open_for_joins))
}
// Danach: QR-Code + Session-Code anzeigen (NFC optional).
// Status wenn jemand joint: "Gerät von Tom hat sich verbunden (2/6)"
```

### Client (Home-Tab, `ui/nav/MainScaffold.kt`)
```kotlin
OutlinedButton(onClick = { /* QR scannen / Code eingeben / NFC lesen */ }) {
    Icon(Icons.Default.QrCodeScanner, null)
    Text(stringResource(R.string.p2p_join_session))
}
```

---

## Profile-Reconciliation UI

Nachdem alle Geräte verbunden sind, sieht jeder Spieler eine Liste aller teilnehmenden Geräte und ordnet sie eigenen lokalen Profilen zu.

**Persistenz:** Mappings in neuer Tabelle `device_profile_mappings` (**Migration 7 → 8**):
```kotlin
@Entity(tableName = "device_profile_mappings")
data class DeviceProfileMappingEntity(
    @PrimaryKey val deviceId: String,
    val profileId: String,
    val createdAt: Long
)
```
Eintrag in `AppDatabase.entities` ergänzen, DB-`version` auf 8 erhöhen, Migration in `Migrations.kt`,
Schema-Export aktualisieren.

---

## Optimistic Locking

```kotlin
class LockManager {
    private val locks = mutableMapOf<String, String>()  // "roundId:profileId" → deviceId

    fun tryLock(roundId: String, profileId: String, deviceId: String): Boolean {
        val key = "$roundId:$profileId"
        return if (!locks.containsKey(key)) { locks[key] = deviceId; true } else false
    }
    fun unlock(roundId: String, profileId: String) { locks.remove("$roundId:$profileId") }
    fun forceUnlock(roundId: String, profileId: String) = unlock(roundId, profileId)
}
```

**UI-Feedback bei Lock:** Button „Karten eingeben für X" → grau + „Wird gerade von [Gerätename] bearbeitet"; kleiner „Entsperren"-Button daneben (für Notfälle).

---

## Verbindungsabbruch + Reconnect

```kotlin
class SessionManager {
    // Heartbeat: Ping alle 5s, Timeout nach 30s → Verbindung als getrennt markieren
    // Bei Trennung: lokaler Betrieb normal weiter
    // Bei Reconnect: Delta-Sync (Host→Client und Client→Host die Änderungen seit Trennung)
    // Conflict-Resolution: Last-Write-Wins per updatedAt-Timestamp (auf allen Entitäten vorhanden)
}
```

---

## Spielende: Datendistribution

Wenn der Host das Spiel schließt:
1. Host sendet `FullGameState(BackupGame)` an alle Clients
2. Clients importieren über die **bestehende Backup-Merge-Logik** (`BackupRepositoryImpl`, Merge-by-UUID
   auf Runden-Granularität) → liefert ein `ImportResult`
3. Bestätigung: „Spieldaten wurden auf dein Gerät übertragen (12 Runden)" (aus `ImportResult.roundsAdded`)

---

## Bekannte Einschränkungen + Risiken

1. **Android Beam ist weg (API 29+).** Der ursprüngliche NFC-Handshake funktioniert nicht mehr → QR/Code
   als primärer Pfad (siehe Handshake-Abschnitt).
2. **Bluetooth muss aktiviert sein.** Ab API 33 darf die App Bluetooth nicht automatisch einschalten →
   Hinweis + Settings-Link statt automatischer Aktivierung.
3. **`BLUETOOTH_CONNECT`-Laufzeitpermission** ab API 31 nötig.
4. **Maximale Clients:** Bluetooth RFCOMM unterstützt typisch 7 gleichzeitige Verbindungen – für max. 6 Spieler ausreichend.
5. **NFC-Verfügbarkeit:** Nicht alle Geräte haben NFC – deshalb ist NFC ohnehin nur optionaler Zusatz.

---

## Akzeptanzkriterien

- [ ] Handshake (QR + Session-Code; NFC optional via Reader-Mode/HCE) überträgt MAC + Session-Token korrekt
- [ ] **Kein `setNdefPushMessage`/Android-Beam mehr** im Code
- [ ] Bluetooth RFCOMM-Verbindung wird direkt (ohne Discovery) aufgebaut
- [ ] Bluetooth-`serviceUuid` ist ein gültiger UUID-Wert (nicht der Paketname)
- [ ] **Kein `ACCESS_FINE_LOCATION` im Manifest**
- [ ] `BLUETOOTH_CONNECT` wird ab API 31 zur Laufzeit abgefragt
- [ ] Spielerliste wird live synchronisiert
- [ ] Lock erscheint bei anderen Geräten wenn jemand anfängt einzugeben; Entsperren-Button funktioniert
- [ ] Verbindungsabbruch: lokaler Betrieb weiter; Reconnect: Delta-Sync bringt alle auf denselben Stand
- [ ] Profile-Reconciliation: Mapping in `device_profile_mappings` persistent (Migration 7→8 vorhanden)
- [ ] Spielende: alle Geräte haben vollständige Daten (über `BackupRepositoryImpl`-Merge)
- [ ] Tests auf mindestens 2 echten Android-Geräten

---

## Strings (Ergänzungen)

```xml
<string name="p2p_open_for_joins">Für Beitritt öffnen</string>
<string name="p2p_join_session">Session beitreten</string>
<string name="p2p_device_joined">Gerät von %1$s verbunden (%2$d/%3$d)</string>
<string name="p2p_locked_by">Wird bearbeitet von %1$s</string>
<string name="p2p_force_unlock">Entsperren</string>
<string name="p2p_reconciliation_title">Spieler zuordnen</string>
<string name="p2p_map_local_profile">Lokales Profil zuordnen</string>
<string name="p2p_sync_complete">Spieldaten synchronisiert</string>
<string name="p2p_bluetooth_off_title">Bluetooth nicht aktiv</string>
<string name="p2p_bluetooth_off_body">Bitte aktiviere Bluetooth in den Einstellungen um der Session beizutreten.</string>
<string name="p2p_bluetooth_settings">Bluetooth-Einstellungen öffnen</string>
<string name="p2p_scan_qr">QR-Code scannen</string>
<string name="p2p_session_code_label">Session-Code</string>
