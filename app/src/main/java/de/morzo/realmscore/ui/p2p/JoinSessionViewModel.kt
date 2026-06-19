package de.morzo.realmscore.ui.p2p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.p2p.HandshakeManager
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.repository.DeviceProfileMappingRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JoinSessionViewModel(
    private val p2p: P2PSessionRepository,
    private val handshake: HandshakeManager,
    private val profileRepo: ProfileRepository,
    private val mappingRepo: DeviceProfileMappingRepository,
) : ViewModel() {

    data class LocalProfile(val id: String, val name: String)

    val sessionState = p2p.sessionState
    val incomingParticipants = p2p.incomingParticipants

    val localProfiles: StateFlow<List<LocalProfile>> =
        profileRepo.observeAll()
            .map { profiles ->
                profiles.filterNot { it.isArchived }.map { LocalProfile(it.id, it.name) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // The P2P session is a process-wide singleton; a previous join may have left it Connected with
        // a stale roster. Reset it so re-entering the join screen starts fresh at the QR scanner
        // instead of jumping straight to the old "joined" view.
        p2p.close()
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

    fun mapDevice(deviceId: String, profileId: String) {
        viewModelScope.launch { mappingRepo.map(deviceId, profileId) }
    }

    fun setError(message: String?) {
        _error.value = message
    }

    class Factory(
        private val p2p: P2PSessionRepository,
        private val handshake: HandshakeManager,
        private val profileRepo: ProfileRepository,
        private val mappingRepo: DeviceProfileMappingRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JoinSessionViewModel(p2p, handshake, profileRepo, mappingRepo) as T
    }
}
