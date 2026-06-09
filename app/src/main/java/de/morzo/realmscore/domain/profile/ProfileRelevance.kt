package de.morzo.realmscore.domain.profile

import kotlin.math.exp

/**
 * Relevance metric for the NewGame autocomplete (Phase 18.1, Punkt 3). A profile's score is the
 * sum, over every game it shared with the owner, of an exponential recency decay – so profiles you
 * played with often *and* recently rank highest. With no shared history the score is 0, letting the
 * caller fall back to alphabetical order.
 */
object ProfileRelevance {
    const val HALF_LIFE_DAYS: Double = 30.0
    private const val MILLIS_PER_DAY: Double = 86_400_000.0

    fun score(sharedGameStartTimes: List<Long>, now: Long): Double =
        sharedGameStartTimes.sumOf { startedAt ->
            val ageDays = (now - startedAt) / MILLIS_PER_DAY
            exp(-ageDays / HALF_LIFE_DAYS)
        }
}
