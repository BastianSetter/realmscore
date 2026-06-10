package de.morzo.realmscore.domain.stats.random

import android.content.Context
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.StatsRepository

class OwnerAvgScoreProvider(
    private val appContext: Context,
    private val profileRepo: ProfileRepository,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "owner_avg_score"
    override suspend fun provide(): RandomStat? {
        val owner = profileRepo.getLocalOwner() ?: return null
        val stats = statsRepo.getPlayerStats(owner.id) ?: return null
        if (stats.gamesPlayed < 1) return null
        val avg = stats.avgScorePerHand.toInt()
        return RandomStat(
            key = key,
            title = appContext.getString(R.string.random_stat_owner_avg, avg),
            visualization = StatVisualization.BigNumber(avg.toString()),
            tapDestination = StatDestination.Player(owner.id),
        )
    }
}

class OwnerBestHandProvider(
    private val appContext: Context,
    private val profileRepo: ProfileRepository,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "owner_best_hand"
    override suspend fun provide(): RandomStat? {
        val owner = profileRepo.getLocalOwner() ?: return null
        val stats = statsRepo.getPlayerStats(owner.id) ?: return null
        if (stats.gamesPlayed < 1) return null
        val best = stats.bestSingleHandScore
        if (best <= 0) return null
        return RandomStat(
            key = key,
            title = appContext.getString(R.string.random_stat_owner_best, best),
            visualization = StatVisualization.BigNumber(best.toString()),
            tapDestination = StatDestination.Player(owner.id),
        )
    }
}

class HighestWinRatePlayerProvider(
    private val appContext: Context,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "highest_winrate_player"
    override suspend fun provide(): RandomStat? {
        val overview = statsRepo.getOverview()
        val top = overview.playerRanking
            .filter { it.gamesPlayed >= 2 }
            .maxByOrNull { it.winRate }
            ?: return null
        val pct = (top.winRate * 100).toInt()
        return RandomStat(
            key = key,
            title = appContext.getString(
                R.string.random_stat_highest_winrate_player,
                top.profile.name,
                pct,
            ),
            visualization = StatVisualization.BigNumber("$pct%"),
            tapDestination = StatDestination.Player(top.profile.id),
        )
    }
}

class MostPopularCardProvider(
    private val appContext: Context,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "most_popular_card"
    override suspend fun provide(): RandomStat? {
        val overview = statsRepo.getOverview()
        val top = overview.cardHits.mostPlayed ?: return null
        return RandomStat(
            key = key,
            title = appContext.getString(
                R.string.random_stat_most_popular_card,
                top.card.displayName(appContext.resources.configuration.locales[0]),
            ),
            visualization = StatVisualization.BigNumber(top.count.toString()),
            tapDestination = StatDestination.Card(top.card.key),
        )
    }
}

class RarestPlayedCardProvider(
    private val appContext: Context,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "rarest_played_card"
    override suspend fun provide(): RandomStat? {
        val overview = statsRepo.getOverview()
        val rare = overview.cardHits.rarestPlayed ?: return null
        return RandomStat(
            key = key,
            title = appContext.getString(
                R.string.random_stat_rarest_played_card,
                rare.card.displayName(appContext.resources.configuration.locales[0]),
            ),
            visualization = StatVisualization.BigNumber(rare.count.toString()),
            tapDestination = StatDestination.Card(rare.card.key),
        )
    }
}

class ClosestRoundEverProvider(
    private val appContext: Context,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "closest_round_ever"
    override suspend fun provide(): RandomStat? {
        val info = statsRepo.getClosestRoundEver() ?: return null
        return RandomStat(
            key = key,
            title = appContext.getString(
                R.string.random_stat_closest_round_ever,
                info.playerA.name,
                info.playerB.name,
                info.difference,
            ),
            visualization = StatVisualization.BigNumber(info.difference.toString()),
            tapDestination = StatDestination.HeadToHead(info.playerA.id, info.playerB.id),
        )
    }
}

class MostPlayedTogetherProvider(
    private val appContext: Context,
    private val statsRepo: StatsRepository,
) : RandomStatProvider {
    override val key = "most_played_together"
    override suspend fun provide(): RandomStat? {
        val info = statsRepo.getMostPlayedPair() ?: return null
        if (info.gamesTogether < 2) return null
        return RandomStat(
            key = key,
            title = appContext.getString(
                R.string.random_stat_most_played_together,
                info.playerA.name,
                info.playerB.name,
                info.gamesTogether,
            ),
            visualization = StatVisualization.BigNumber(info.gamesTogether.toString()),
            tapDestination = StatDestination.HeadToHead(info.playerA.id, info.playerB.id),
        )
    }
}
