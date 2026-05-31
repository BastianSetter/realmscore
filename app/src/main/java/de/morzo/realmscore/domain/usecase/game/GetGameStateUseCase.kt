package de.morzo.realmscore.domain.usecase.game

import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.GameParticipant
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

data class GameParticipantWithProfile(
    val participant: GameParticipant,
    val profile: Profile,
)

data class GameState(
    val game: Game,
    val participants: List<GameParticipantWithProfile>,
    val rounds: List<Round>,
    val resultsByRoundAndProfile: Map<Pair<String, String>, Int>,
    val totalScoresByProfile: Map<String, Int>,
    val leadingProfileId: String?,
    val isPointLimitReached: Boolean,
    val hasOpenRound: Boolean,
)

class GetGameStateUseCase(
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val profileRepo: ProfileRepository,
) {

    fun observe(gameId: String): Flow<GameState> = flow {
        val game = gameRepo.getById(gameId)
            ?: error("Game not found: $gameId")
        val participantEntries = gameRepo.getParticipants(gameId)
        val participants = participantEntries.mapNotNull { entry ->
            profileRepo.getById(entry.profileId)?.let { profile ->
                GameParticipantWithProfile(entry, profile)
            }
        }
        emitAll(
            combine(
                roundRepo.observeRoundsForGame(gameId),
                roundRepo.observeResultsForGame(gameId),
            ) { rounds, results ->
                val resultsMap: Map<Pair<String, String>, Int> = results.associate {
                    (it.roundId to it.profileId) to it.totalScore
                }
                buildState(game, participants, rounds, resultsMap)
            }
        )
    }

    private fun buildState(
        game: Game,
        participants: List<GameParticipantWithProfile>,
        rounds: List<Round>,
        resultsMap: Map<Pair<String, String>, Int>,
    ): GameState {
        val totals: Map<String, Int> = participants.associate { p ->
            p.profile.id to rounds.sumOf { round ->
                resultsMap[round.id to p.profile.id] ?: 0
            }
        }
        val leading = totals.entries
            .filter { it.value > 0 }
            .maxByOrNull { it.value }
            ?.key
        val hasOpenRound = rounds.any { it.completedAt == null }
        val pointLimitReached = game.mode == GameMode.POINT_LIMIT &&
            game.targetPoints != null &&
            totals.values.any { it >= game.targetPoints }

        return GameState(
            game = game,
            participants = participants,
            rounds = rounds,
            resultsByRoundAndProfile = resultsMap,
            totalScoresByProfile = totals,
            leadingProfileId = leading,
            isPointLimitReached = pointLimitReached,
            hasOpenRound = hasOpenRound,
        )
    }
}
