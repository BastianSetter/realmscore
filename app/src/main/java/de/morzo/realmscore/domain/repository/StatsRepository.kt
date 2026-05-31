package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.stats.CardStats
import de.morzo.realmscore.domain.stats.CardStatsRow
import de.morzo.realmscore.domain.stats.CardStatsSort
import de.morzo.realmscore.domain.stats.ClosestRoundInfo
import de.morzo.realmscore.domain.stats.GlobalStats
import de.morzo.realmscore.domain.stats.HeadToHeadStats
import de.morzo.realmscore.domain.stats.PlayerPairInfo
import de.morzo.realmscore.domain.stats.PlayerStats
import de.morzo.realmscore.domain.stats.StatsOverview

interface StatsRepository {
    suspend fun getGlobalStats(): GlobalStats
    suspend fun getOverview(): StatsOverview
    suspend fun getPlayerStats(profileId: String): PlayerStats?
    suspend fun getCardStats(cardKey: String): CardStats?
    suspend fun getCardStatsOverview(sortBy: CardStatsSort): List<CardStatsRow>
    suspend fun getHeadToHeadStats(profileIdA: String, profileIdB: String): HeadToHeadStats?
    suspend fun getClosestRoundEver(): ClosestRoundInfo?
    suspend fun getMostPlayedPair(): PlayerPairInfo?
}
