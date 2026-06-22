package de.morzo.realmscore.ui.p2p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.p2p.HandshakeManager
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JoinSessionViewModel(
    private val p2p: P2PSessionRepository,
    private val handshake: HandshakeManager,
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    data class LocalProfile(val id: String, val name: String, val colorArgb: Int)

    val sessionState = p2p.sessionState
    val incomingParticipants = p2p.incomingParticipants

    /** The last joined host, if any (§6 #2): drives the silent "Session erneut beitreten" reconnect. */
    val rejoinInfo = p2p.rejoinInfo

    /** Host-driven navigation: when the host starts a round, follow it into capture (Stage B). */
    val navSignals = p2p.navSignals

    /**
     * Gültige lokale Merge-Ziele für eingehende Profile: aktive (nicht archiviert, nicht selbst gemergt)
     * Profile inkl. Owner — geräteunabhängig (Ziel darf ein zuvor von einem anderen Host kopiertes
     * Profil sein). Zusätzlich werden Profile ausgeblendet, die bereits im Spiel sind: entweder selbst
     * im Roster sitzen oder schon als Merge-Ziel eines Roster-Teilnehmers gewählt wurden (verhindert,
     * dass zwei Teilnehmer demselben lokalen Profil zugewiesen werden). Das ist die A/B/C/D-Auswahl.
     */
    val localProfiles: StateFlow<List<LocalProfile>> =
        combine(profileRepo.observeAll(), incomingParticipants) { profiles, roster ->
            val rosterIds = roster.map { it.profileId }.toSet()
            // Lokale Profile, die bereits als Merge-Ziel eines sitzenden Roster-Teilnehmers dienen.
            val assignedTargetIds = profiles
                .filter { it.id in rosterIds && it.mergeTargetId != null }
                .mapNotNull { it.mergeTargetId }
                .toSet()
            val inGame = rosterIds + assignedTargetIds
            profiles
                .filter { !it.isArchived && it.mergeTargetId == null && it.id !in inGame }
                .map { LocalProfile(it.id, it.name, it.colorArgb) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Bereits erfolgte Zuordnungen: fremde Roster-`profileId` → Name des lokalen Merge-Ziels. Speist
     * die „Zusammengeführt mit …“-Anzeige im Roster. Aufgelöst aus dem Merge-Zeiger des (lokal via
     * [ProfileRepository.ensureRemoteProfile] angelegten) fremden Profils.
     */
    val assignments: StateFlow<Map<String, String>> =
        profileRepo.observeAll()
            .map { profiles ->
                val byId = profiles.associateBy { it.id }
                profiles
                    .filter { it.mergeTargetId != null }
                    .mapNotNull { p -> byId[p.mergeTargetId]?.let { p.id to it.name } }
                    .toMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Id des lokalen Owner-Profils: das eigene Seat im Roster ist nicht zuweisbar (Button ausblenden). */
    val localOwnerId: StateFlow<String?> =
        profileRepo.observeAll()
            .map { profiles -> profiles.firstOrNull { it.isLocalOwner }?.id }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // The P2P session is a process-wide singleton; a previous *finished* join may have left it
        // Connected with a stale roster. Reset it so re-entering the join screen starts fresh at the QR
        // scanner — but NOT while a game is in progress (that would silently drop us from the live game).
        if (!p2p.isInActiveGame()) p2p.close()
    }

    fun bluetoothStatus(): BluetoothStatus = p2p.bluetoothStatus()

    /** Parse scanned QR text into a payload; null if it isn't one of ours. */
    fun parseQr(text: String): HandshakePayload? {
        val parsed = handshake.parseScannedPayload(text)
        if (parsed == null) _error.value = "invalid_qr"
        return parsed
    }

    /**
     * Camera-less path: the user typed the 6-digit code. We have no host Bluetooth name to pre-filter
     * the CDM picker, so the system lists all nearby devices; [code] travels as the session token and
     * is validated in-band by the host (Stage B).
     */
    fun payloadForManualCode(code: String): HandshakePayload = HandshakePayload(
        hostDeviceId = "",
        hostBluetoothName = "",
        gameId = "",
        sessionToken = code,
    )

    fun connect(payload: HandshakePayload, macAddress: String) {
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()
            if (owner == null) {
                _error.value = "no_local_owner"
                return@launch
            }
            // "Join adds a player": announce ourselves as our own local owner profile. The host
            // reconciles this to a local profile and drops it straight into the game roster.
            val self = ParticipantInfo(
                profileId = owner.id,
                name = owner.name,
                colorArgb = owner.colorArgb,
                seatOrder = 0, // the host reassigns seat order when it appends us
                originDeviceId = owner.originDeviceId,
            )
            p2p.connectToHost(payload, macAddress, self)
                .onFailure { _error.value = it.message ?: "connect_failed" }
        }
    }

    /**
     * Silent rejoin (§6 #2): reconnect to the last host with the persisted MAC + payload, no QR scan.
     * Reuses [connect] (and thus the normal connect path) so a failure surfaces the scanner as usual.
     */
    fun rejoin() {
        val info = rejoinInfo.value ?: return
        connect(info.payload, info.macAddress)
    }

    /**
     * Drop the current (possibly stale) connection and forget the stored last host, so the screen falls
     * back to the QR scanner and the user can join a *different* session. Needed when a previous game was
     * abandoned/killed (no GameClosed) and the persisted host keeps auto-rejoining.
     */
    fun forgetLastSession() {
        p2p.close()
        p2p.clearRejoinInfo()
    }

    /**
     * Weist ein eingehendes (fremdes) Roster-Profil einem vorhandenen lokalen Profil zu: legt das fremde
     * Profil bei Bedarf lokal an und setzt seinen Merge-Zeiger aufs gewählte lokale Profil. Ab dann
     * zählen dessen Spiele zur Laufzeit unter dem lokalen Profil (kein destruktives Umschreiben).
     */
    fun assignMerge(incoming: ParticipantInfo, localProfileId: String) {
        viewModelScope.launch {
            _error.value = null
            // Das eigene Owner-Profil ist im Roster sichtbar, lässt sich aber nicht zusammenführen
            // (ProfileRepository.setMergeTarget wirft). Vorab abfangen → freundliche Meldung statt
            // verschluckter Exception.
            if (profileRepo.getById(incoming.profileId)?.isLocalOwner == true) {
                _error.value = ERROR_ASSIGN_OWNER
                return@launch
            }
            runCatching {
                profileRepo.ensureRemoteProfile(
                    id = incoming.profileId,
                    name = incoming.name,
                    colorArgb = incoming.colorArgb,
                    originDeviceId = incoming.originDeviceId,
                )
                profileRepo.setMergeTarget(incoming.profileId, localProfileId)
            }.onFailure { _error.value = ERROR_ASSIGN_FAILED }
        }
    }

    fun setError(message: String?) {
        _error.value = message
    }

    class Factory(
        private val p2p: P2PSessionRepository,
        private val handshake: HandshakeManager,
        private val profileRepo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JoinSessionViewModel(p2p, handshake, profileRepo) as T
    }

    companion object {
        /** Stabile Fehler-Keys; die UI übersetzt sie in lokalisierte Texte. */
        const val ERROR_ASSIGN_OWNER = "assign_owner"
        const val ERROR_ASSIGN_FAILED = "assign_failed"
    }
}
