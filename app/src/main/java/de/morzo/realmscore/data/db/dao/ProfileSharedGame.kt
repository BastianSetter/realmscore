package de.morzo.realmscore.data.db.dao

/** One game that [profileId] played together with the owner, plus when it started. */
data class ProfileSharedGame(
    val profileId: String,
    val startedAt: Long,
)
