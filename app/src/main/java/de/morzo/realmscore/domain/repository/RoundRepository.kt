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
    fun observeRoundById(id: String): Flow<Round?>
    suspend fun markRoundCompleted(roundId: String)

    /** Card keys captured in the central discard area (Mittelfeld) of [roundId], in entry order. */
    suspend fun getDiscardCards(roundId: String): List<String>

    /** Reactive variant of [getDiscardCards] — emits whenever the discard capture changes. */
    fun observeDiscardCards(roundId: String): Flow<List<String>>

    /**
     * Replaces the captured discard cards for [roundId] with [cardKeys] and marks the round's
     * `discardScanned` flag true (Phase 20). Saving an empty list still counts as "scanned".
     */
    suspend fun saveDiscardCards(roundId: String, cardKeys: List<String>)

    /**
     * Returns a map keyed by `gameId -> profileId -> totalScore` summed across all rounds.
     * Games without any results are omitted entirely.
     */
    suspend fun getScoreTotalsForGames(gameIds: List<String>): Map<String, Map<String, Int>>
}
