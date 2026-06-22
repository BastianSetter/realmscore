package de.morzo.realmscore.domain.model

data class Profile(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isLocalOwner: Boolean,
    val isArchived: Boolean,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
    // Phase 0 (Profil-Rework): explizite Identität (deviceId, profileId) als abfragbare Spalten neben
    // dem Surrogat-PK `id` (= "$deviceId:$profileId"). `mergeTargetId` zeigt non-destruktiv auf das
    // kanonische Profil (Surrogat-id), in das dieses Profil verschmolzen ist. Defaults halten die
    // Zwischenstände der Phasen kompilierbar; Phase 1 belegt sie bei der Erzeugung korrekt.
    val deviceId: String = "",
    val profileId: String = "",
    val mergeTargetId: String? = null,
) {
    /** Owner-Profil des Geräts: profileId == deviceId (siehe Phase-1-Erzeugung). */
    val isOwner: Boolean get() = profileId.isNotBlank() && profileId == deviceId

    /** Wurde non-destruktiv in ein anderes Profil verschmolzen. */
    val isMerged: Boolean get() = mergeTargetId != null
}
