package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.morzo.realmscore.domain.model.RoundResult

@Entity(
    tableName = "round_results",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
        ),
    ],
    indices = [Index("roundId"), Index("profileId")],
)
data class RoundResultEntity(
    @PrimaryKey val id: String,
    val roundId: String,
    val profileId: String,
    val totalScore: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
) {
    fun toDomain(): RoundResult = RoundResult(
        id = id,
        roundId = roundId,
        profileId = profileId,
        totalScore = totalScore,
        createdAt = createdAt,
        updatedAt = updatedAt,
        originDeviceId = originDeviceId,
    )

    companion object {
        fun fromDomain(result: RoundResult): RoundResultEntity = RoundResultEntity(
            id = result.id,
            roundId = result.roundId,
            profileId = result.profileId,
            totalScore = result.totalScore,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
            originDeviceId = result.originDeviceId,
        )
    }
}
