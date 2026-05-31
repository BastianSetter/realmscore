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
)
