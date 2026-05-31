package de.morzo.realmscore.domain.scoring.rules.common

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.blanking.BlankingEffect
import de.morzo.realmscore.domain.scoring.penalty.PenaltyCancellation

class CompositeRule(private val parts: List<CardScoringRule>) : CardScoringRule {
    constructor(vararg parts: CardScoringRule) : this(parts.toList())

    override fun cancellations(self: ResolvedCard, ctx: ScoringContext): List<PenaltyCancellation> =
        parts.flatMap { it.cancellations(self, ctx) }

    override fun blanking(self: ResolvedCard, ctx: ScoringContext): List<BlankingEffect> =
        parts.flatMap { it.blanking(self, ctx) }

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        parts.flatMap { it.bonuses(self, ctx) }

    override fun penalties(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        parts.flatMap { it.penalties(self, ctx) }
}
