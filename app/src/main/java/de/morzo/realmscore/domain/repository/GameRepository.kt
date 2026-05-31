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
}
