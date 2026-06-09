package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.DiscardCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscardCardDao {

    @Insert
    suspend fun insertAll(cards: List<DiscardCardEntity>)

    @Query("SELECT cardKey FROM discard_cards WHERE roundId = :roundId ORDER BY position ASC")
    suspend fun getCardKeysForRound(roundId: String): List<String>

    @Query("SELECT cardKey FROM discard_cards WHERE roundId = :roundId ORDER BY position ASC")
    fun observeCardKeysForRound(roundId: String): Flow<List<String>>

    @Query("DELETE FROM discard_cards WHERE roundId = :roundId")
    suspend fun deleteAllForRound(roundId: String)

    @Query("DELETE FROM discard_cards")
    suspend fun deleteAll()
}
