package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.GameParticipant
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeOpenGames(): Flow<List<Game>>
    fun observeAllGames(): Flow<List<Game>>
    fun observeClosedGames(): Flow<List<Game>>
    suspend fun getById(id: String): Game?
    suspend fun getParticipants(gameId: String): List<GameParticipant>
    suspend fun getParticipantsForGames(gameIds: List<String>): List<GameParticipant>
    suspend fun startGame(
        mode: GameMode,
        target: Int,
        participantProfileIds: List<String>,
        displayName: String? = null,
    ): Game

    suspend fun closeGame(gameId: String, reason: ClosedReason)
    suspend fun reopenGame(gameId: String)

    /**
     * Phase 18.1, Punkt 2: persist the scan order of a round into each participant's
     * [GameParticipant.lastScanOrder], so the next round defaults to the same order.
     * Keys are profileIds; values are 0-based positions (0 = captured first).
     */
    suspend fun updateScanOrder(gameId: String, profileIdToOrder: Map<String, Int>)
}
