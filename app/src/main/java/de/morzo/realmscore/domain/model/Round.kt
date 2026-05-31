package de.morzo.realmscore.domain.model

data class Round(
    val id: String,
    val gameId: String,
    val roundNumber: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val discardScanned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)
