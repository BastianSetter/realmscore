package de.morzo.realmscore.domain.scoring.rules.common

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.HandCondition
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.blanking.BlankingEffect

/** "Is blanked if condition holds" — Kriegsschiff, Kampfzeppelin, Rauch. */
class SelfBlankIfRule(private val condition: HandCondition) : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext): List<BlankingEffect> =
        listOf(BlankingEffect.SelfBlank(sourceKey = self.originalKey, condition = condition))
}
