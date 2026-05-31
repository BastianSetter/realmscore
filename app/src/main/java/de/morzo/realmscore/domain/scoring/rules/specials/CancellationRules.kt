package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.penalty.PenaltyCancellation

/** Rune des Schutzes: cancels every penalty in the hand. */
object RuneOfProtectionRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(PenaltyCancellation.CancelAll(self.originalKey))
}

/** Höhle: cancels every Weather penalty (and gives +25 with Dwarvish Infantry or Dragon — composed elsewhere). */
object CavernCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(PenaltyCancellation.CancelBySuit(self.originalKey, setOf(Suit.WEATHER)))
}

/** Gebirge: cancels every Flood penalty (and gives +50 with Smoke+Wildfire — composed elsewhere). */
object MountainCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(PenaltyCancellation.CancelBySuit(self.originalKey, setOf(Suit.FLOOD)))
}

/** Herr der Bestien: cancels every Beast penalty (paired with +9/Beast — composed elsewhere). */
object BeastmasterCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(PenaltyCancellation.CancelBySuit(self.originalKey, setOf(Suit.BEAST)))
}

/** Waldläufer: strips "Army" word from every penalty (paired with +10/Land). */
object RangersCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(PenaltyCancellation.StripSuitWord(self.originalKey, strippedSuits = setOf(Suit.ARMY)))
}

/** Kriegsschiff: strips "Army" word from FLOOD penalties only (paired with self-blank). */
object WarshipCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(
            PenaltyCancellation.StripSuitWord(
                sourceKey = self.originalKey,
                strippedSuits = setOf(Suit.ARMY),
                scopeSuits = setOf(Suit.FLOOD),
            )
        )
}

/** Insel: cancels one Flood OR Flame penalty chosen by the player. */
object IslandCancelRule : CardScoringRule {
    override fun cancellations(self: ResolvedCard, ctx: ScoringContext) =
        listOf(
            PenaltyCancellation.CancelOneOf(
                sourceKey = self.originalKey,
                targetCardKey = ctx.playerChoices.islandTargetKey,
            )
        )
}
