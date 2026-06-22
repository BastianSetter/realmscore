package de.morzo.realmscore.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.SessionState
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
    /** True on the P2P host: "Neues Spiel starten" should bring the joined phones along. */
    val isP2pHost: Boolean = false,
    /** True on a joined phone: it can't start the next game, so it keeps "Zur Startseite". */
    val isP2pClient: Boolean = false,
)

class GameSummaryViewModel(
    private val gameId: String,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val profileRepo: ProfileRepository,
    private val p2p: P2PSessionRepository,
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
                        isP2pHost = p2p.sessionState.value is SessionState.Hosting,
                        isP2pClient = p2p.sessionState.value is SessionState.Connected,
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
            // P2P (Stage B): distribute the finished game to all joined devices (no-op when solo). The
            // game row is already closed, so its export carries closedAt to every client.
            p2p.closeSharedGame(gameId)
            _uiState.update { it.copy(isClosed = true) }
            onClosed()
        }
    }

    /**
     * "Zurück zum Hauptmenü" from the game-end screen: drop any live P2P session (close all sockets →
     * [SessionState.Idle]) so the user returns to a cold-start state — no open connections. The game is
     * already closed here, so the joined phones' rejoin info was cleared by GameClosed; on a solo game
     * this is a harmless no-op. [onLeft] then handles the navigation (home + clear the Game back stack).
     */
    fun leaveToMenu(onLeft: () -> Unit) {
        p2p.close()
        onLeft()
    }

    /**
     * "Neues Spiel starten" from the game-end screen. On the P2P host, first tell the joined phones the
     * next game is being set up (they show a waiting screen) and proceed with [continueSession] = true so
     * the new-game screen keeps the live session and brings them along. Solo just opens a fresh setup.
     */
    fun prepareNewGame(onProceed: (continueSession: Boolean) -> Unit) {
        viewModelScope.launch {
            val hosting = p2p.sessionState.value is SessionState.Hosting
            if (hosting) p2p.announceNewGameSetup()
            onProceed(hosting)
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
        private val p2p: P2PSessionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameSummaryViewModel(
                gameId = gameId,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                profileRepo = profileRepo,
                p2p = p2p,
            ) as T
        }
    }
}
