package de.morzo.realmscore.domain.scoring.rules

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardMatcher.AnyOf
import de.morzo.realmscore.domain.scoring.CardMatcher.ByKey
import de.morzo.realmscore.domain.scoring.CardMatcher.BySuit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.HandCondition.Contains
import de.morzo.realmscore.domain.scoring.rules.common.CompositeRule
import de.morzo.realmscore.domain.scoring.rules.common.ConditionalFlatRule
import de.morzo.realmscore.domain.scoring.rules.common.PerOtherCountRule
import de.morzo.realmscore.domain.scoring.rules.common.SelfBlankIfRule
import de.morzo.realmscore.domain.scoring.rules.specials.CastleRule
import de.morzo.realmscore.domain.scoring.rules.specials.ChapelRule
import de.morzo.realmscore.domain.scoring.rules.specials.CryptRule
import de.morzo.realmscore.domain.scoring.rules.specials.DEMON_KEY
import de.morzo.realmscore.domain.scoring.rules.specials.DemonRule
import de.morzo.realmscore.domain.scoring.rules.specials.DiscardCountRule
import de.morzo.realmscore.domain.scoring.rules.specials.DungeonRule
import de.morzo.realmscore.domain.scoring.rules.specials.ExpansionGreatFloodRule
import de.morzo.realmscore.domain.scoring.rules.specials.FountainOfLifeRule
import de.morzo.realmscore.domain.scoring.rules.specials.GenieRule
import de.morzo.realmscore.domain.scoring.rules.specials.JudgeRule
import de.morzo.realmscore.domain.scoring.rules.specials.NECROMANCER_KEYS
import de.morzo.realmscore.domain.scoring.rules.specials.RangersCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.WorldTreeBonusRule

/**
 * Phase 30: card key → rule for the expansion "Der verfluchte Schatz" (new suits + replacement
 * cards). Merged into the base map by [BaseGameRules.build]; keys never collide (`expansion_` prefix).
 *
 * Effects that are not a per-card bonus/penalty live in the engine:
 *  - Dämon's pre-blanking step ([DemonRule.earlyBlanked]),
 *  - "cannot be BLANKED" (Engel self + its chosen target; all Undead with Lich / expansion
 *    Necromancer in hand),
 *  - Kobold / Dschinn draws and the expansion Necromancer pull are plain extra hand cards.
 * Mirage / Shapeshifter / Necromancer have no rule of their own (JokerResolver), as in the base game.
 */
object ExpansionRules {

    fun map(): Map<String, CardScoringRule> = mapOf(
        // ───────────── UNDEAD ─────────────
        "expansion_death_knight" to DiscardCountRule(
            suits = setOf(Suit.WEAPON, Suit.ARMY),
            amountPer = 7,
            descriptionKey = "effect_death_knight",
        ),
        "expansion_lich" to PerOtherCountRule(
            matcher = AnyOf(BySuit(Suit.UNDEAD), ByKey(NECROMANCER_KEYS)),
            amountPer = 10,
            descriptionKey = "effect_lich",
        ),
        "expansion_ghost" to DiscardCountRule(
            suits = setOf(Suit.WIZARD, Suit.ARTIFACT, Suit.OUTSIDER),
            amountPer = 6,
            descriptionKey = "effect_ghost",
        ),
        "expansion_ghoul" to DiscardCountRule(
            suits = setOf(Suit.WIZARD, Suit.LEADER, Suit.ARMY, Suit.BEAST, Suit.UNDEAD),
            amountPer = 4,
            descriptionKey = "effect_ghoul",
        ),
        "expansion_dark_queen" to DiscardCountRule(
            suits = setOf(Suit.LAND, Suit.FLOOD, Suit.FLAME, Suit.WEATHER),
            keys = setOf("unicorn"),
            amountPer = 5,
            descriptionKey = "effect_dark_queen",
        ),

        // ───────────── OUTSIDER ─────────────
        DEMON_KEY to DemonRule,
        // expansion_leprechaun — base strength only; its drawn card is captured as a normal hand card.
        // expansion_angel — protection is applied by the engine (JokerType.ANGEL target).
        "expansion_judge" to JudgeRule,
        "expansion_genie" to GenieRule,

        // ───────────── LAND ─────────────
        "expansion_garden" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.LEADER, Suit.BEAST),
                amountPer = 11,
                descriptionKey = "effect_garden",
            ),
            SelfBlankIfRule(Contains(AnyOf(BySuit(Suit.UNDEAD), ByKey(NECROMANCER_KEYS + DEMON_KEY)))),
        ),

        // ───────────── BUILDING ─────────────
        "expansion_chapel" to ChapelRule,
        "expansion_crypt" to CryptRule,
        "expansion_castle" to CastleRule,
        "expansion_dungeon" to DungeonRule,
        "expansion_bell_tower" to ConditionalFlatRule(
            condition = Contains(BySuit(Suit.WIZARD, Suit.UNDEAD)),
            amount = 15,
            descriptionKey = "effect_expansion_bell_tower",
        ),

        // ───────────── Replacements of base cards ─────────────
        "expansion_world_tree" to WorldTreeBonusRule(70),
        "expansion_rangers" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.LAND, Suit.BUILDING),
                amountPer = 10,
                descriptionKey = "effect_expansion_rangers",
            ),
            RangersCancelRule,
        ),
        "expansion_great_flood" to ExpansionGreatFloodRule,
        "expansion_fountain_of_life" to FountainOfLifeRule,
    )
}
