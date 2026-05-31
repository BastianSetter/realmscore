package de.morzo.realmscore.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PodiumEntry(
    val rank: Int,
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val totalScore: Int,
)

data class GameStats(
    val roundCount: Int,
    val highestSingleHandScore: Int,
    val highestSingleHandPlayer: String,
    val highestSingleHandRound: Int,
    val closestRoundDifference: Int,
    val closestRoundNumber: Int,
)

data class RoundsTableRow(
    val roundId: String,
    val roundNumber: Int,
    val scoresByProfile: Map<String, Int>,
    val winnerProfileId: String?,
)

data class GameSummaryUiState(
    val isLoading: Boolean = true,
    val isClosed: Boolean = false,
    val podium: List<PodiumEntry> = emptyList(),
    val rounds: List<RoundsTableRow> = emptyList(),
    val totalsByProfile: Map<String, Int> = emptyMap(),
    val players: List<Profile> = emptyList(),
    val gameStats: GameStats? = null,
)

class GameSummaryViewModel(
    private val gameId: String,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameSummaryUiState())
    val uiState: StateFlow<GameSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val participants = gameRepo.getParticipants(gameId).sortedBy { it.seatOrder }
            val players = participants.mapNotNull { profileRepo.getById(it.profileId) }

            combine(
                roundRepo.observeRoundsForGame(gameId),
                roundRepo.observeResultsForGame(gameId),
            ) { rounds, results ->
                rounds to results
            }.collect { (rounds, results) ->
                val game = gameRepo.getById(gameId) ?: return@collect
                val completedRounds = rounds.filter { it.completedAt != null }
                    .sortedBy { it.roundNumber }
                val totals = computeTotals(players, completedRounds, results)
                val podium = computePodium(players, totals)
                val table = completedRounds.map { round -> buildRow(round, results) }
                val stats = computeStats(completedRounds, results, players)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isClosed = game.closedAt != null,
                        podium = podium,
                        rounds = table,
                        totalsByProfile = totals,
                        players = players,
                        gameStats = stats,
                    )
                }
            }
        }
    }

    fun closeAndNavigate(onClosed: () -> Unit) {
        viewModelScope.launch {
            val current = gameRepo.getById(gameId)
            if (current?.closedAt == null) {
                gameRepo.closeGame(gameId, ClosedReason.COMPLETED)
            }
            _uiState.update { it.copy(isClosed = true) }
            onClosed()
        }
    }

    private fun computeTotals(
        players: List<Profile>,
        rounds: List<Round>,
        results: List<RoundResult>,
    ): Map<String, Int> {
        val resultsByRound = results.groupBy { it.roundId }
        return players.associate { profile ->
            profile.id to rounds.sumOf { round ->
                resultsByRound[round.id]?.firstOrNull { it.profileId == profile.id }?.totalScore
                    ?: 0
            }
        }
    }

    private fun computePodium(
        players: List<Profile>,
        totals: Map<String, Int>,
    ): List<PodiumEntry> {
        if (players.isEmpty()) return emptyList()
        val sortedScores = totals.values.distinct().sortedDescending()
        val rankByScore = sortedScores.mapIndexed { index, score -> score to index + 1 }.toMap()

        return players
            .map { profile ->
                val score = totals[profile.id] ?: 0
                PodiumEntry(
                    rank = rankByScore[score] ?: (players.size),
                    profileId = profile.id,
                    name = profile.name,
                    colorArgb = profile.colorArgb,
                    totalScore = score,
                )
            }
            .filter { it.rank <= 3 }
            .sortedBy { it.rank }
    }

    private fun buildRow(round: Round, results: List<RoundResult>): RoundsTableRow {
        val resultsForRound = results.filter { it.roundId == round.id }
        val scoresByProfile = resultsForRound.associate { it.profileId to it.totalScore }
        val maxScore = resultsForRound.maxOfOrNull { it.totalScore }
        val winners = if (maxScore != null) {
            resultsForRound.filter { it.totalScore == maxScore }
        } else {
            emptyList()
        }
        val winnerId = if (winners.size == 1) winners.first().profileId else null
        return RoundsTableRow(
            roundId = round.id,
            roundNumber = round.roundNumber,
            scoresByProfile = scoresByProfile,
            winnerProfileId = winnerId,
        )
    }

    private fun computeStats(
        completedRounds: List<Round>,
        results: List<RoundResult>,
        players: List<Profile>,
    ): GameStats? {
        if (completedRounds.isEmpty()) return null

        val playerNameById = players.associate { it.id to it.name }

        val highestResult = results.maxByOrNull { it.totalScore }
        val highestRoundNumber = highestResult?.let { res ->
            completedRounds.firstOrNull { it.id == res.roundId }?.roundNumber ?: 0
        } ?: 0
        val highestName = highestResult?.profileId?.let { playerNameById[it] }.orEmpty()
        val highestScore = highestResult?.totalScore ?: 0

        val closestPair = completedRounds.mapNotNull { round ->
            val scoresForRound = results.filter { it.roundId == round.id }.map { it.totalScore }
            if (scoresForRound.size < 2) return@mapNotNull null
            val diff = abs(scoresForRound.max() - scoresForRound.min())
            round.roundNumber to diff
        }.minByOrNull { it.second }

        return GameStats(
            roundCount = completedRounds.size,
            highestSingleHandScore = highestScore,
            highestSingleHandPlayer = highestName,
            highestSingleHandRound = highestRoundNumber,
            closestRoundDifference = closestPair?.second ?: 0,
            closestRoundNumber = closestPair?.first ?: 0,
        )
    }

    class Factory(
        private val gameId: String,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val profileRepo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameSummaryViewModel(
                gameId = gameId,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                profileRepo = profileRepo,
            ) as T
        }
    }
}
