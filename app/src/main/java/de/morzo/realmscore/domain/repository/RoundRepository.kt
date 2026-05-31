package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import kotlinx.coroutines.flow.Flow

interface RoundRepository {
    fun observeRoundsForGame(gameId: String): Flow<List<Round>>
    suspend fun getOpenRound(gameId: String): Round?
    suspend fun startRound(gameId: String): Round
    fun observeResults(roundId: String): Flow<List<RoundResult>>
    fun observeResultsForGame(gameId: String): Flow<List<RoundResult>>
    suspend fun getRoundById(id: String): Round?
    suspend fun markRoundCompleted(roundId: String)

    /**
     * Card keys that were observed in the central discard area of [roundId].
     * MVP collects no discard data, so this currently returns an empty list.
     * Kept on the interface so the Sandbox prefill path is ready for Phase 2.
     */
    suspend fun getDiscardCards(roundId: String): List<String>

    /**
     * Returns a map keyed by `gameId -> profileId -> totalScore` summed across all rounds.
     * Games without any results are omitted entirely.
     */
    suspend fun getScoreTotalsForGames(gameIds: List<String>): Map<String, Map<String, Int>>
}
