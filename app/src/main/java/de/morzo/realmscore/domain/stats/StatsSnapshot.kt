package de.morzo.realmscore.domain.stats

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.HandCard
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult

/**
 * Immutable snapshot of all stats-relevant data. Built once per
 * [de.morzo.realmscore.domain.repository.StatsRepository] call; passed to the pure
 * helper functions in [StatsCalculator].
 *
 * Only closed games + their completed rounds are included.
 */
data class StatsSnapshot(
    val closedGames: List<Game>,
    val participantsByGame: Map<String, List<Profile>>,
    val completedRoundsByGame: Map<String, List<Round>>,
    val resultsByRoundResultId: Map<String, RoundResult>,
    val resultsByRoundId: Map<String, List<RoundResult>>,
    val handCardsByResultId: Map<String, List<HandCard>>,
    val perCardContributionByResultId: Map<String, Map<String, Int>>,
    val profilesById: Map<String, Profile>,
    val cardsByKey: Map<String, CardDefinition>,
) {
    val allCompletedRounds: List<Round> by lazy {
        completedRoundsByGame.values.flatten()
    }

    val roundsByRoundId: Map<String, Round> by lazy {
        allCompletedRounds.associateBy { it.id }
    }

    val gamesById: Map<String, Game> by lazy {
        closedGames.associateBy { it.id }
    }
}
