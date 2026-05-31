package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.HandCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HandCardDao {

    @Insert
    suspend fun insert(card: HandCardEntity)

    @Insert
    suspend fun insertAll(cards: List<HandCardEntity>)

    @Query("DELETE FROM hand_cards WHERE roundResultId = :rrId")
    suspend fun deleteAllForRoundResult(rrId: String)

    @Query("SELECT * FROM hand_cards WHERE roundResultId = :rrId ORDER BY position")
    suspend fun getForRoundResult(rrId: String): List<HandCardEntity>

    @Query(
        "SELECT rr.profileId AS profileId, COUNT(hc.id) AS handCardCount " +
            "FROM round_results rr " +
            "LEFT JOIN hand_cards hc ON hc.roundResultId = rr.id " +
            "WHERE rr.roundId = :roundId " +
            "GROUP BY rr.profileId"
    )
    fun observeHandCountsByProfile(roundId: String): Flow<List<ProfileHandCount>>

    @Query(
        "SELECT hc.cardKey FROM hand_cards hc " +
            "INNER JOIN round_results rr ON hc.roundResultId = rr.id " +
            "WHERE rr.roundId = :roundId AND rr.profileId != :excludeProfileId"
    )
    fun observeCardKeysForRoundExcluding(
        roundId: String,
        excludeProfileId: String,
    ): Flow<List<String>>

    @Query("SELECT * FROM hand_cards WHERE roundResultId IN (:resultIds)")
    suspend fun getForRoundResults(resultIds: List<String>): List<HandCardEntity>

    @Query("DELETE FROM hand_cards")
    suspend fun deleteAll()
}

data class ProfileHandCount(
    val profileId: String,
    val handCardCount: Int,
)
