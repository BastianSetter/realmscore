# Phase 25 – P2P-Sync (NFC + Bluetooth)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 24 abgeschlossen. **Dies ist die technisch komplexeste Phase. Sorgfältig lesen und bei Unklarheiten vor der Implementierung nachfragen.**

---

## Kontext (kurz)

Mehrere Spieler am selben Tisch können über P2P eine gemeinsame Spielsession teilen. Jeder tippt seine eigenen Karten ein, alle sehen denselben Stand. Profile werden zwischen Geräten abgeglichen (Merge-Logik). Am Ende hat jedes Gerät das vollständige Spiel lokal gespeichert.

**Technische Entscheidung:** Host-basiert. Das Gerät das das Spiel gestartet hat ist der Host. Andere Geräte verbinden sich als Clients. Einfacher als Mesh, für diesen Use Case ausreichend.

**Protokoll:** NFC für den initialen Handshake (Device-ID-Austausch), dann WiFi-Direct für die laufende Session.

---

## Scope

### Drin
- Session-Setup via NFC (Host & Client)
- WiFi-Direct Verbindung für Live-Daten
- Live-Sync der Spielerliste beim Spielerstellen
- Optimistic Locking für Karteneingaben
- Profile-Reconciliation (lokales Profil ↔ fremde Device-ID)
- Resilientes Offline-Weiter bei Verbindungsabbruch + Sync beim Reconnect
- Vollständige Datenverteilung am Spielende auf alle Geräte

### Explizit NICHT drin
- Kein Cloud-Relay (alles lokal)
- Kein permanenter Hintergrund-Sync (nur während einer aktiven Session)
- Keine Unterstützung für mehr als 6 Geräte gleichzeitig (max 6 Spieler)

---

## Was am Ende funktionieren muss

### Session-Setup
1. Spieler A erstellt Spiel → Button "Für NFC-Joins öffnen" erscheint
2. Spieler B öffnet App → "Per NFC beitreten"-Button im Home-Tab
3. Spieler B hält Handy an Handy von A → NFC überträgt Host-Verbindungsinfos
4. B sieht die Spielerliste von A in Echtzeit
5. A tippt "Maria" → erscheint bei B sofort
6. B kann für sich "Lokales Profil zuordnen" ("Maria auf Gerät B ist mein Profil Maria")
7. A und B starten das Spiel gemeinsam

### Während des Spiels
8. B tippt auf "Karten für Profil XY eingeben" → Button wird bei A als "geblockt (B)" markiert
9. B gibt Karten ein → werden live zu A synchronisiert
10. Verbindungsabbruch: beide Geräte speichern lokal weiter
11. Reconnect: Delta-Sync (nur Änderungen seit Trennung)

### Spielende
12. Alle Geräte haben vollständige Spieldaten lokal

---

## Tech-Stack (F-Droid-konform, keine Location-Permission)

**Protokoll-Wahl:** Bluetooth Classic RFCOMM statt WiFi-Direct.

**Warum nicht WiFi-Direct?**
WiFi-Direct benötigt ab API 29 `ACCESS_FINE_LOCATION` für die Peer-Discovery-Phase. Das wollen wir vermeiden.

**Warum Bluetooth ohne Location?**
Location wird nur für Bluetooth-Discovery (unbekannte Geräte finden) benötigt. Da wir die Bluetooth-MAC-Adresse via NFC übertragen, brauchen wir keine Discovery → keine Location-Permission.

```kotlin
// libs.versions.toml – KEINE neuen Libraries nötig
// Bluetooth Classic RFCOMM ist Teil des Android SDK
```

### Benötigte Permissions (minimal!)

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.NFC" />

<!-- Bluetooth API 26-30 -->
<uses-permission android:name="android.permission.BLUETOOTH"
    android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
    android:maxSdkVersion="30" />

<!-- Bluetooth API 31+ -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-feature android:name="android.hardware.nfc" android:required="false" />
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
```

**Kein `ACCESS_FINE_LOCATION`!** Der Verzicht auf Discovery macht das möglich.

---

## Architektur

```kotlin
// data/p2p/
├── NfcHandshakeManager.kt     // NFC: Bluetooth-MAC + Session-Token übertragen
├── BluetoothRfcommManager.kt  // Bluetooth RFCOMM: Verbindungsaufbau + Datentransfer
├── SessionManager.kt          // High-Level: Session-State, Clients verwalten
├── SyncProtocol.kt            // Nachrichten-Format
└── LockManager.kt             // Optimistic Locking

// domain/p2p/
├── P2PSessionRepository.kt    // Interface
└── model/
    ├── SessionState.kt
    ├── SyncMessage.kt
    └── DeviceLock.kt
```

---

## NFC-Handshake

NFC überträgt die Bluetooth-MAC-Adresse des Hosts + Session-Token:

```kotlin
data class NfcHandshakePayload(
    val hostDeviceId: String,
    val bluetoothMacAddress: String,  // z.B. "AA:BB:CC:DD:EE:FF"
    val gameId: String,
    val sessionToken: String          // UUID zur Verifikation
)
```

Ablauf:
1. **Host:** `NfcAdapter.setNdefPushMessage()` mit `NfcHandshakePayload` als JSON
2. **Client:** empfängt Payload → verbindet sich direkt per Bluetooth an die bekannte MAC-Adresse (**kein Scan, keine Discovery**)

---

## Bluetooth RFCOMM Verbindung

```kotlin
class BluetoothRfcommManager(private val context: Context) {
    private val adapter = BluetoothAdapter.getDefaultAdapter()
    private val serviceUuid = UUID.fromString("de.morzo.realmscore.p2p")

    // Host: wartet auf eingehende Verbindungen
    suspend fun startServer(): BluetoothServerSocket {
        return adapter.listenUsingRfcommWithServiceRecord("RealmScore", serviceUuid)
    }

    // Client: verbindet sich an bekannte MAC (kein Discovery nötig!)
    suspend fun connectToHost(macAddress: String): BluetoothSocket {
        val device = adapter.getRemoteDevice(macAddress)  // direkt per MAC, kein Scan
        return device.createRfcommSocketToServiceRecord(serviceUuid)
            .also { it.connect() }
    }
}

interface P2PConnection {
    suspend fun send(message: SyncMessage)
    fun receive(): Flow<SyncMessage>
    fun isConnected(): Boolean
    fun close()
}
```

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
    @Serializable data class FullGameState(val game: GameBackupData) : SyncMessage()  // beim Reconnect
    @Serializable data class UnlockRequest(val profileId: String, val roundId: String) : SyncMessage()  // forciertes Entsperren
    @Serializable data class DeviceJoined(val deviceId: String, val deviceName: String) : SyncMessage()
    @Serializable data class Ping : SyncMessage()
    @Serializable data class Pong : SyncMessage()
}
```

---

## Session-Setup UI

### Host (NewGameScreen)
```kotlin
// Wenn Spiel angelegt und NFC verfügbar:
if (nfcAvailable) {
    OutlinedButton(
        onClick = { sessionManager.openForNfcJoins(game) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Nfc, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.p2p_open_for_joins))
    }
}

// Status wenn jemand joint:
// "Gerät von Tom hat sich verbunden (2/6)"
```

### Client (Home-Tab)
```kotlin
// Kleiner Banner im Home-Tab wenn NFC verfügbar:
if (nfcAvailable) {
    OutlinedButton(onClick = { nfcManager.listenForSession(activity, onJoin = ...) }) {
        Icon(Icons.Default.Nfc, null)
        Text(stringResource(R.string.p2p_join_via_nfc))
    }
}
```

---

## Profile-Reconciliation UI

Nachdem alle Geräte verbunden sind, sieht jeder Spieler eine Liste aller teilnehmenden Geräte:

```kotlin
@Composable
fun ParticipantReconciliationSheet(
    remoteDevices: List<RemoteDevice>,
    localProfiles: List<Profile>,
    existingMappings: Map<String, String>,  // deviceId → localProfileId
    onMapDevice: (deviceId: String, localProfileId: String?) -> Unit
) {
    // Pro Remote-Device:
    // - Geräte-Name + Device-ID (gekürzt)
    // - Falls schon gematcht: zeigt lokalen Profil-Namen
    // - Button "Lokales Profil zuordnen" → Dropdown der eigenen Profile
    // - Diese Zuordnung wird persistent gespeichert (für zukünftige Spiele)
}
```

**Persistenz:** Mappings in einer neuen Tabelle `device_profile_mappings`:
```kotlin
@Entity(tableName = "device_profile_mappings")
data class DeviceProfileMappingEntity(
    @PrimaryKey val deviceId: String,
    val profileId: String,
    val createdAt: Long
)
```

---

## Optimistic Locking

```kotlin
class LockManager {
    private val locks = mutableMapOf<String, String>()  // "roundId:profileId" → deviceId

    fun tryLock(roundId: String, profileId: String, deviceId: String): Boolean {
        val key = "$roundId:$profileId"
        return if (!locks.containsKey(key)) {
            locks[key] = deviceId
            true
        } else false
    }

    fun unlock(roundId: String, profileId: String) {
        locks.remove("$roundId:$profileId")
    }

    fun forceUnlock(roundId: String, profileId: String) {
        // Ausgelöst durch "Entsperren"-Button anderer Geräte
        unlock(roundId, profileId)
    }
}
```

**UI-Feedback bei Lock:**
- Button "Karten eingeben für X" → grau + "Wird gerade von [Gerätename] bearbeitet"
- Kleiner "Entsperren"-Button daneben (für Notfälle)

---

## Verbindungsabbruch + Reconnect

```kotlin
class SessionManager {
    // Heartbeat: Ping alle 5s, Timeout nach 30s → Verbindung als getrennt markieren
    // Bei Trennung: lokaler Betrieb normal weiter
    // Bei Reconnect: Delta-Sync
    //   Host → Client: alle Änderungen seit letztem ACK
    //   Client → Host: alle lokalen Änderungen seit Trennung

    // Conflict-Resolution bei Delta-Sync: Last-Write-Wins per Timestamp
    // (UUID + Timestamp auf allen Sync-Events)
}
```

---

## Spielende: Datendistribution

Wenn der Host das Spiel schließt:
1. Host sendet `FullGameState` an alle Clients
2. Clients importieren die kompletten Spieldaten (Merge-Logik wie in Phase 23)
3. Bestätigung: "Spieldaten wurden auf dein Gerät übertragen (12 Runden)"

---

## Bekannte Einschränkungen + Risiken

1. **Bluetooth-Verfügbarkeit:** Alle modernen Android-Geräte haben Bluetooth – aber der User muss es aktiviert haben. Hinweis im UI wenn Bluetooth aus ist.
2. **Bluetooth muss manuell aktiviert sein:** Ab API 33 darf die App Bluetooth nicht mehr automatisch einschalten. Hinweis + Settings-Link statt automatische Aktivierung.
3. **Maximale Clients:** Bluetooth RFCOMM unterstützt typisch 7 gleichzeitige Verbindungen – für max. 6 Spieler ausreichend.
4. **NFC-Verfügbarkeit:** Nicht alle Geräte haben NFC. Fallback: 6-stelliger Session-Code manuell eingeben.

```kotlin
// Fallback wenn kein NFC:
@Composable
fun JoinFallbackOptions(sessionId: String) {
    // QR-Code anzeigen/scannen
    // ODER: 6-stelligen Session-Code manuell eingeben
}
```

---

## Akzeptanzkriterien

- [ ] NFC-Handshake überträgt Bluetooth-MAC + Session-Token korrekt
- [ ] Bluetooth RFCOMM-Verbindung wird direkt (ohne Discovery) aufgebaut
- [ ] **Kein `ACCESS_FINE_LOCATION` im Manifest**
- [ ] Spielerliste wird live synchronisiert
- [ ] Lock erscheint bei anderen Geräten wenn jemand anfängt einzugeben
- [ ] Entsperren-Button funktioniert
- [ ] Verbindungsabbruch: lokaler Betrieb weiter
- [ ] Reconnect: Delta-Sync bringt alle auf denselben Stand
- [ ] Profile-Reconciliation: Mapping wird persistent gespeichert
- [ ] Spielende: alle Geräte haben vollständige Daten
- [ ] Fallback wenn kein NFC vorhanden
- [ ] Hinweis auf Location-Permission mit Erklärung
- [ ] Tests auf mindestens 2 echten Android-Geräten

---

## Strings (Ergänzungen)

```xml
<string name="p2p_open_for_joins">Für NFC-Joins öffnen</string>
<string name="p2p_join_via_nfc">Per NFC beitreten</string>
<string name="p2p_device_joined">Gerät von %1$s verbunden (%2$d/%3$d)</string>
<string name="p2p_locked_by">Wird bearbeitet von %1$s</string>
<string name="p2p_force_unlock">Entsperren</string>
<string name="p2p_reconciliation_title">Spieler zuordnen</string>
<string name="p2p_map_local_profile">Lokales Profil zuordnen</string>
<string name="p2p_sync_complete">Spieldaten synchronisiert</string>
<string name="p2p_bluetooth_off_title">Bluetooth nicht aktiv</string>
<string name="p2p_bluetooth_off_body">Bitte aktiviere Bluetooth in den Einstellungen um der Session beizutreten.</string>
<string name="p2p_bluetooth_settings">Bluetooth-Einstellungen öffnen</string>
<string name="p2p_no_nfc_fallback_title">Kein NFC verfügbar</string>
<string name="p2p_session_code_label">Session-Code</string>
```
