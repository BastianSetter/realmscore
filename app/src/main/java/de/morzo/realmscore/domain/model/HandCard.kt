package de.morzo.realmscore.domain.model

data class HandCard(
    val id: String,
    val roundResultId: String,
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
