package de.morzo.realmscore.domain.model

data class GameParticipant(
    val gameId: String,
    val profileId: String,
    val seatOrder: Int,
    val lastScanOrder: Int?,
)
