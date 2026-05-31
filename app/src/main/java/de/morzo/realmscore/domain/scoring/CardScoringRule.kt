package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.scoring.blanking.BlankingEffect
import de.morzo.realmscore.domain.scoring.penalty.PenaltyCancellation

/**
 * One per card key. Engine calls these in pipeline order:
 *   1) cancellations() during cancellation collection (penaltyContext is null then)
 *   2) blanking() during blanking fixpoint (penaltyContext is null then)
 *   3) bonuses() during bonus phase (penaltyContext is populated)
 *   4) penalties() during penalty phase (penaltyContext is populated)
 *
 * Rules MUST be reentrant + idempotent: same self+ctx → same output.
 */
interface CardScoringRule {
    fun cancellations(self: ResolvedCard, ctx: ScoringContext): List<PenaltyCancellation> = emptyList()
    fun blanking(self: ResolvedCard, ctx: ScoringContext): List<BlankingEffect> = emptyList()
    fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> = emptyList()
    fun penalties(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> = emptyList()
}
