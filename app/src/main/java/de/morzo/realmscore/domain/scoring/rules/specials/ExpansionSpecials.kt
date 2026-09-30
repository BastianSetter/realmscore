package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardMatcher
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.blanking.BlankingEffect

// Phase 30 – special rules of the expansion "Der verfluchte Schatz".

/** Keys of both Necromancer editions (base + expansion); several expansion cards name "Totenbeschwörer". */
val NECROMANCER_KEYS = setOf("necromancer", "expansion_necromancer")

const val DEMON_KEY = "expansion_demon"
const val ANGEL_KEY = "expansion_angel"
const val LICH_KEY = "expansion_lich"
const val EXPANSION_NECROMANCER_KEY = "expansion_necromancer"

/**
 * Undead that score the discard area (Todesritter, Gespenst, Ghul, Dunkle Königin): +[amountPer] for
 * each captured Mittelfeld card matching [suits] or [keys]. A joker lying in the discard area keeps
 * its printed WILD suit — it can NOT count as another suit (rulebook p. 7), which falls out naturally
 * because discard cards are plain definitions.
 */
class DiscardCountRule(
    private val suits: Set<Suit>,
    private val amountPer: Int,
    private val descriptionKey: String,
    private val keys: Set<String> = emptySet(),
) : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val count = ctx.discardPile.count { it.suit in suits || it.key in keys }
        if (count == 0) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = descriptionKey,
                descriptionArgs = listOf(count.toString()),
                pointsDelta = amountPer * count,
            )
        )
    }
}

/** Weltenbaum: +[amount] if every non-blanked card has a unique effective suit (base 50, expansion 70). */
class WorldTreeBonusRule(private val amount: Int) : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val suits = ctx.nonBlankedHand().map { it.effectiveSuit }
        if (suits.size < 2 || suits.size != suits.toSet().size) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_world_tree",
                pointsDelta = amount,
            )
        )
    }
}

/** Große Flut (expansion): blanks all Armies, all Buildings, all Lands except Mountain, all Flames except Lightning. */
object ExpansionGreatFloodRule : CardScoringRule {
    override fun blanking(self: ResolvedCard, ctx: ScoringContext) = listOf(
        BlankingEffect.BlankBySuitExcept(sourceKey = self.originalKey, targetSuits = setOf(Suit.ARMY, Suit.BUILDING)),
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

/**
 * Dämon: "For each non-Outsider card: if it is the only card of its suit in your hand, it is
 * BLANKED. This happens before all other blanking." The engine asks [earlyBlanked] once, after the
 * jokers are resolved and cancellations collected but before the blanking fixpoint (rulebook step 5);
 * suits are counted on the resolved hand. Blanked cards would not count — at this point nothing is
 * blanked yet, so the whole resolved hand is the pool.
 */
object DemonRule : CardScoringRule {
    fun earlyBlanked(self: ResolvedCard, hand: List<ResolvedCard>): Set<String> {
        val suitCounts = hand.groupingBy { it.effectiveSuit }.eachCount()
        return hand
            .filter { it.originalKey != self.originalKey && it.effectiveSuit != Suit.OUTSIDER }
            .filter { suitCounts[it.effectiveSuit] == 1 }
            .map { it.originalKey }
            .toSet()
    }
}

/**
 * Richter: +10 for each card in hand with a penalty that is not CLEARED. A partly cleared penalty
 * (e.g. the word "Army" stripped by the Rangers) still counts (FAQ). A card "has a penalty" when its
 * printed rule text carries a "Strafe:" section; a Doppelganger copies penalties and therefore counts
 * too, Mirage/Shapeshifter copy none and don't.
 */
object JudgeRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val pc = ctx.penaltyContext
        val matched = ctx.nonBlankedHand().filter { card ->
            card.originalKey != self.originalKey &&
                card.penaltyEnabled &&
                ctx.cardLookup(card.effectiveCardKey)?.ruleTextDe?.contains("Strafe:") == true &&
                pc?.isFullyCancelled(card) != true
        }
        if (matched.isEmpty()) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_judge",
                descriptionArgs = listOf(matched.size.toString()),
                pointsDelta = 10 * matched.size,
                contributingCardKeys = matched.map { it.originalKey },
            )
        )
    }
}

/** Dschinn: +10 for each opponent (players − 1). Unknown player count → no bonus. */
object GenieRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val opponents = (ctx.playerCount ?: return emptyList()) - 1
        if (opponents <= 0) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_genie",
                descriptionArgs = listOf(opponents.toString()),
                pointsDelta = 10 * opponents,
            )
        )
    }
}

/**
 * Kapelle: +40 if the hand holds exactly two cards among Leader, Wizard, Outsider and Undead
 * (combined, any mix — FAQ).
 */
object ChapelRule : CardScoringRule {
    private val SUITS = setOf(Suit.LEADER, Suit.WIZARD, Suit.OUTSIDER, Suit.UNDEAD)

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val matched = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey && it.effectiveSuit in SUITS }
        if (matched.size != 2) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_chapel",
                pointsDelta = 40,
                contributingCardKeys = matched.map { it.originalKey },
            )
        )
    }
}

/** Gruft: bonus = the sum of the base strengths of all Undead in hand; penalty: blanks all Leaders. */
object CryptRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val undead = ctx.nonBlankedHand().filter {
            it.originalKey != self.originalKey && it.effectiveSuit == Suit.UNDEAD && it.effectiveStrength != 0
        }
        if (undead.isEmpty()) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_crypt",
                descriptionArgs = listOf(undead.size.toString()),
                pointsDelta = undead.sumOf { it.effectiveStrength },
                contributingCardKeys = undead.map { it.originalKey },
                contributorWeights = undead.map { it.effectiveStrength },
            )
        )
    }

    override fun blanking(self: ResolvedCard, ctx: ScoringContext) = listOf(
        BlankingEffect.BlankBySuit(sourceKey = self.originalKey, targetSuits = setOf(Suit.LEADER)),
    )
}

/**
 * Burg: +10 each for the first Leader, Army, Land and other Building; +5 for every further Building
 * beyond that first other one (the Castle itself never counts).
 */
object CastleRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val others = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey }
        val effects = mutableListOf<EffectApplication>()
        for (suit in listOf(Suit.LEADER, Suit.ARMY, Suit.LAND, Suit.BUILDING)) {
            val first = others.firstOrNull { it.effectiveSuit == suit } ?: continue
            effects += EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_castle_first_${suit.name.lowercase()}",
                pointsDelta = 10,
                contributingCardKeys = listOf(first.originalKey),
            )
        }
        val furtherBuildings = others.filter { it.effectiveSuit == Suit.BUILDING }.drop(1)
        if (furtherBuildings.isNotEmpty()) {
            effects += EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_castle_more_buildings",
                descriptionArgs = listOf(furtherBuildings.size.toString()),
                pointsDelta = 5 * furtherBuildings.size,
                contributingCardKeys = furtherBuildings.map { it.originalKey },
            )
        }
        return effects
    }
}

/**
 * Verlies: +10 each for the first Undead, Beast and Artifact; +5 for every further card of these
 * suits and for each Necromancer, Warlock and Demon.
 */
object DungeonRule : CardScoringRule {
    private val EXTRA_KEYS = NECROMANCER_KEYS + setOf("warlock", DEMON_KEY)
    private val extraMatcher = CardMatcher.ByKey(EXTRA_KEYS)

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val others = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey }
        val effects = mutableListOf<EffectApplication>()
        val further = mutableListOf<String>()
        for (suit in listOf(Suit.UNDEAD, Suit.BEAST, Suit.ARTIFACT)) {
            val ofSuit = others.filter { it.effectiveSuit == suit }
            val first = ofSuit.firstOrNull() ?: continue
            effects += EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_dungeon_first_${suit.name.lowercase()}",
                pointsDelta = 10,
                contributingCardKeys = listOf(first.originalKey),
            )
            further += ofSuit.drop(1).map { it.originalKey }
        }
        further += others.filter { extraMatcher.matches(it) && it.originalKey !in further }.map { it.originalKey }
        if (further.isNotEmpty()) {
            effects += EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_dungeon_more",
                descriptionArgs = listOf(further.size.toString()),
                pointsDelta = 5 * further.size,
                contributingCardKeys = further,
            )
        }
        return effects
    }
}
