package de.morzo.realmscore.domain.model

data class RoundResult(
    val id: String,
    val roundId: String,
    val profileId: String,
    val totalScore: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)
