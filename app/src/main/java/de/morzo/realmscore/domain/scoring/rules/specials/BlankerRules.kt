package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.blanking.BlankingEffect

/** Basilisk: blanks all Armies, Leaders, and other Beasts. */
object BasiliskRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) =
        listOf(
            BlankingEffect.BlankBySuit(
                sourceKey = self.originalKey,
                targetSuits = setOf(Suit.ARMY, Suit.LEADER, Suit.BEAST),
                excludeSelf = true,
            )
        )
}

/** Buschfeuer: blanks every card except Flame/Wizard/Weather/Weapon/Artefact suits + named exceptions. */
object WildfireRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) =
        listOf(
            BlankingEffect.BlankAllExcept(
                sourceKey = self.originalKey,
                allowedSuits = setOf(
                    Suit.FLAME,
                    Suit.WIZARD,
                    Suit.WEATHER,
                    Suit.WEAPON,
                    Suit.ARTIFACT,
                ),
                allowedKeys = setOf(
                    "mountain",
                    "great_flood",
                    "island",
                    "unicorn",
                    "dragon",
                ),
                excludeSelf = true,
            )
        )
}

/** Große Flut: blanks all Army, all Land except Mountain, all Flame except Lightning. */
object GreatFloodRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) = listOf(
        BlankingEffect.BlankBySuitExcept(
            sourceKey = self.originalKey,
            targetSuits = setOf(Suit.ARMY),
        ),
        BlankingEffect.BlankBySuitExcept(
            sourceKey = self.originalKey,
            targetSuits = setOf(Suit.LAND),
            exceptKeys = setOf("mountain"),
        ),
        BlankingEffect.BlankBySuitExcept(
            sourceKey = self.originalKey,
            targetSuits = setOf(Suit.FLAME),
            exceptKeys = setOf("lightning"),
        ),
    )
}

/** Rainstorm-blanker portion: blanks all Flames except Lightning (combined with +10/Flood elsewhere). */
object RainstormBlankerRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) = listOf(
        BlankingEffect.BlankBySuitExcept(
            sourceKey = self.originalKey,
            targetSuits = setOf(Suit.FLAME),
            exceptKeys = setOf("lightning"),
        )
    )
}

/** Blizzard-blanker portion: blanks all Floods (combined with -5/listed-suit elsewhere). */
object BlizzardBlankerRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) = listOf(
        BlankingEffect.BlankBySuit(
            sourceKey = self.originalKey,
            targetSuits = setOf(Suit.FLOOD),
            excludeSelf = true,
        )
    )
}
