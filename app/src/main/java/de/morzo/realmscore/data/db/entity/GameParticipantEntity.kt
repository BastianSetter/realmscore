package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.morzo.realmscore.domain.model.GameParticipant

@Entity(
    tableName = "game_participants",
    primaryKeys = ["gameId", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
        ),
    ],
    indices = [Index("profileId")],
)
data class GameParticipantEntity(
    val gameId: String,
    val profileId: String,
    val seatOrder: Int,
    val lastScanOrder: Int?,
) {
    fun toDomain(): GameParticipant = GameParticipant(
        gameId = gameId,
        profileId = profileId,
        seatOrder = seatOrder,
        lastScanOrder = lastScanOrder,
    )

    companion object {
        fun fromDomain(participant: GameParticipant): GameParticipantEntity = GameParticipantEntity(
            gameId = participant.gameId,
            profileId = participant.profileId,
            seatOrder = participant.seatOrder,
            lastScanOrder = participant.lastScanOrder,
        )
    }
}
