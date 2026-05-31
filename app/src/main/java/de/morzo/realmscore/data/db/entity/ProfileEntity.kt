package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.morzo.realmscore.domain.model.Profile

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int,
    val isLocalOwner: Boolean,
    val isArchived: Boolean,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
) {
    fun toDomain(): Profile = Profile(
        id = id,
        name = name,
        colorArgb = colorArgb,
        isLocalOwner = isLocalOwner,
        isArchived = isArchived,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        originDeviceId = originDeviceId,
    )

    companion object {
        fun fromDomain(profile: Profile): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            colorArgb = profile.colorArgb,
            isLocalOwner = profile.isLocalOwner,
            isArchived = profile.isArchived,
            archivedAt = profile.archivedAt,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
            originDeviceId = profile.originDeviceId,
        )
    }
}
