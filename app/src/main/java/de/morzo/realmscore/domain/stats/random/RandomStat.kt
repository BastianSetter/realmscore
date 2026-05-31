package de.morzo.realmscore.domain.stats.random

data class RandomStat(
    val key: String,
    val title: String,
    val visualization: StatVisualization,
    val tapDestination: StatDestination?,
)

sealed class StatVisualization {
    data class BigNumber(val value: String) : StatVisualization()
    data class BarChart(val values: List<Float>, val labels: List<String>) : StatVisualization()
    data class LineChart(val points: List<Float>) : StatVisualization()
}

sealed class StatDestination {
    data class Player(val profileId: String) : StatDestination()
    data class Card(val cardKey: String) : StatDestination()
    data class HeadToHead(val profileIdA: String, val profileIdB: String) : StatDestination()
    object Overview : StatDestination()
}

interface RandomStatProvider {
    val key: String
    suspend fun provide(): RandomStat?
}

sealed class RandomStatResult {
    object NotEnoughData : RandomStatResult()
    data class Found(val stat: RandomStat) : RandomStatResult()
}
