package de.morzo.realmscore.domain.scoring.rules

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardMatcher.AnyOf
import de.morzo.realmscore.domain.scoring.CardMatcher.ByKey
import de.morzo.realmscore.domain.scoring.CardMatcher.BySuit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.HandCondition.And
import de.morzo.realmscore.domain.scoring.HandCondition.Contains
import de.morzo.realmscore.domain.scoring.HandCondition.NotContains
import de.morzo.realmscore.domain.scoring.HandCondition.Or
import de.morzo.realmscore.domain.scoring.rules.common.CompositeRule
import de.morzo.realmscore.domain.scoring.rules.common.ConditionalFlatRule
import de.morzo.realmscore.domain.scoring.rules.common.FlatPenaltyIfMissingRule
import de.morzo.realmscore.domain.scoring.rules.common.PerOtherCountRule
import de.morzo.realmscore.domain.scoring.rules.common.SelfBlankIfRule
import de.morzo.realmscore.domain.scoring.rules.specials.BasiliskRule
import de.morzo.realmscore.domain.scoring.rules.specials.BeastmasterCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.BlizzardBlankerRule
import de.morzo.realmscore.domain.scoring.rules.specials.CavernCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.CollectorRule
import de.morzo.realmscore.domain.scoring.rules.specials.FountainOfLifeRule
import de.morzo.realmscore.domain.scoring.rules.specials.GemOfOrderRule
import de.morzo.realmscore.domain.scoring.rules.specials.GreatFloodRule
import de.morzo.realmscore.domain.scoring.rules.specials.IslandCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.KingRule
import de.morzo.realmscore.domain.scoring.rules.specials.MountainCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.QueenRule
import de.morzo.realmscore.domain.scoring.rules.specials.RainstormBlankerRule
import de.morzo.realmscore.domain.scoring.rules.specials.RangersCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.RuneOfProtectionRule
import de.morzo.realmscore.domain.scoring.rules.specials.ShieldOfKethRule
import de.morzo.realmscore.domain.scoring.rules.specials.SwordOfKethRule
import de.morzo.realmscore.domain.scoring.rules.specials.UnicornRule
import de.morzo.realmscore.domain.scoring.rules.specials.WarlordRule
import de.morzo.realmscore.domain.scoring.rules.specials.WarshipCancelRule
import de.morzo.realmscore.domain.scoring.rules.specials.WildfireRule
import de.morzo.realmscore.domain.scoring.rules.specials.WorldTreeRule

/**
 * Single source of truth: 53 card keys → CardScoringRule.
 * Cards whose only contribution is base strength (jokers without assignment, Necromancer
 * in phase-05 sandbox without discard) get no entry.
 */
object BaseGameRules {

    fun build(): CardRuleRegistry = CardRuleRegistry(map())

    private fun map(): Map<String, CardScoringRule> = mapOf(
        // ───────────── ARMY ─────────────
        "rangers" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.LAND),
                amountPer = 10,
                descriptionKey = "effect_rangers_per_land",
            ),
            RangersCancelRule,
        ),
        "elven_archers" to ConditionalFlatRule(
            condition = NotContains(BySuit(Suit.WEATHER)),
            amount = 5,
            descriptionKey = "effect_elven_archers_no_weather",
        ),
        "dwarvish_infantry" to PerOtherCountRule(
            matcher = BySuit(Suit.ARMY),
            amountPer = -2,
            descriptionKey = "effect_dwarvish_infantry_per_army",
            isPenalty = true,
        ),
        "light_cavalry" to PerOtherCountRule(
            matcher = BySuit(Suit.LAND),
            amountPer = -2,
            descriptionKey = "effect_light_cavalry_per_land",
            isPenalty = true,
        ),
        "knights" to FlatPenaltyIfMissingRule(
            matcher = BySuit(Suit.LEADER),
            amount = 8,
            descriptionKey = "effect_knights_no_leader",
        ),

        // ───────────── ARTEFACT ─────────────
        "protection_rune" to RuneOfProtectionRule,
        "world_tree" to WorldTreeRule,
        // book_of_changes — joker; handled by JokerResolver, no own rule
        "shield_of_keth" to ShieldOfKethRule,
        "gem_of_order" to GemOfOrderRule,

        // ───────────── BEAST ─────────────
        "warhorse" to ConditionalFlatRule(
            condition = Contains(BySuit(Suit.LEADER, Suit.WIZARD)),
            amount = 14,
            descriptionKey = "effect_warhorse_leader_or_wizard",
        ),
        "unicorn" to UnicornRule,
        "hydra" to ConditionalFlatRule(
            condition = Contains(ByKey("swamp")),
            amount = 28,
            descriptionKey = "effect_hydra_swamp",
        ),
        "dragon" to FlatPenaltyIfMissingRule(
            matcher = BySuit(Suit.WIZARD),
            amount = 40,
            descriptionKey = "effect_dragon_no_wizard",
        ),
        "basilisk" to BasiliskRule,

        // ───────────── FLAME ─────────────
        "candle" to ConditionalFlatRule(
            condition = And(
                Contains(ByKey("book_of_changes")),
                Contains(ByKey("bell_tower")),
                Contains(BySuit(Suit.WIZARD)),
            ),
            amount = 100,
            descriptionKey = "effect_candle_combo",
        ),
        "fire_elemental" to PerOtherCountRule(
            matcher = BySuit(Suit.FLAME),
            amountPer = 15,
            descriptionKey = "effect_fire_elemental_per_flame",
        ),
        "forge" to PerOtherCountRule(
            matcher = BySuit(Suit.WEAPON, Suit.ARTIFACT),
            amountPer = 9,
            descriptionKey = "effect_forge_per_weapon_or_artifact",
        ),
        "lightning" to ConditionalFlatRule(
            condition = Contains(ByKey("rainstorm")),
            amount = 30,
            descriptionKey = "effect_lightning_rainstorm",
        ),
        "wildfire" to WildfireRule,

        // ───────────── FLOOD ─────────────
        "fountain_of_life" to FountainOfLifeRule,
        "water_elemental" to PerOtherCountRule(
            matcher = BySuit(Suit.FLOOD),
            amountPer = 15,
            descriptionKey = "effect_water_elemental_per_flood",
        ),
        "island" to IslandCancelRule,
        "swamp" to PerOtherCountRule(
            matcher = BySuit(Suit.ARMY, Suit.FLAME),
            amountPer = -3,
            descriptionKey = "effect_swamp_per_army_or_flame",
            isPenalty = true,
        ),
        "great_flood" to GreatFloodRule,

        // ───────────── LAND ─────────────
        "earth_elemental" to PerOtherCountRule(
            matcher = BySuit(Suit.LAND),
            amountPer = 15,
            descriptionKey = "effect_earth_elemental_per_land",
        ),
        "cavern" to CompositeRule(
            ConditionalFlatRule(
                condition = Contains(ByKey("dwarvish_infantry", "dragon")),
                amount = 25,
                descriptionKey = "effect_cavern_dwarves_or_dragon",
            ),
            CavernCancelRule,
        ),
        "forest" to PerOtherCountRule(
            matcher = AnyOf(BySuit(Suit.BEAST), ByKey("elven_archers")),
            amountPer = 12,
            descriptionKey = "effect_forest_per_beast_or_archers",
        ),
        "bell_tower" to ConditionalFlatRule(
            condition = Contains(BySuit(Suit.WIZARD)),
            amount = 15,
            descriptionKey = "effect_bell_tower_wizard",
        ),
        "mountain" to CompositeRule(
            ConditionalFlatRule(
                condition = And(
                    Contains(ByKey("smoke")),
                    Contains(ByKey("wildfire")),
                ),
                amount = 50,
                descriptionKey = "effect_mountain_smoke_wildfire",
            ),
            MountainCancelRule,
        ),

        // ───────────── LEADER ─────────────
        "princess" to PerOtherCountRule(
            matcher = BySuit(Suit.ARMY, Suit.WIZARD, Suit.LEADER),
            amountPer = 8,
            descriptionKey = "effect_princess_per_target",
        ),
        "warlord" to WarlordRule,
        "king" to KingRule,
        "queen" to QueenRule,
        "empress" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.ARMY),
                amountPer = 10,
                descriptionKey = "effect_empress_per_army",
            ),
            PerOtherCountRule(
                matcher = BySuit(Suit.LEADER),
                amountPer = -5,
                descriptionKey = "effect_empress_per_other_leader",
                isPenalty = true,
            ),
        ),

        // ───────────── WEAPON ─────────────
        "magic_wand" to ConditionalFlatRule(
            condition = Contains(BySuit(Suit.WIZARD)),
            amount = 25,
            descriptionKey = "effect_magic_wand_wizard",
        ),
        "elven_longbow" to ConditionalFlatRule(
            condition = Contains(ByKey("elven_archers", "warlord", "beastmaster")),
            amount = 30,
            descriptionKey = "effect_elven_longbow_target",
        ),
        "sword_of_keth" to SwordOfKethRule,
        "warship" to CompositeRule(
            SelfBlankIfRule(NotContains(BySuit(Suit.FLOOD))),
            WarshipCancelRule,
        ),
        "war_dirigible" to SelfBlankIfRule(
            Or(
                NotContains(BySuit(Suit.ARMY)),
                Contains(BySuit(Suit.WEATHER)),
            )
        ),

        // ───────────── WEATHER ─────────────
        "air_elemental" to PerOtherCountRule(
            matcher = BySuit(Suit.WEATHER),
            amountPer = 15,
            descriptionKey = "effect_air_elemental_per_weather",
        ),
        "rainstorm" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.FLOOD),
                amountPer = 10,
                descriptionKey = "effect_rainstorm_per_flood",
            ),
            RainstormBlankerRule,
        ),
        "whirlwind" to ConditionalFlatRule(
            condition = And(
                Contains(ByKey("rainstorm")),
                Or(
                    Contains(ByKey("blizzard")),
                    Contains(ByKey("great_flood")),
                ),
            ),
            amount = 40,
            descriptionKey = "effect_whirlwind_combo",
        ),
        "smoke" to SelfBlankIfRule(NotContains(BySuit(Suit.FLAME))),
        "blizzard" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.ARMY, Suit.LEADER, Suit.BEAST, Suit.FLAME),
                amountPer = -5,
                descriptionKey = "effect_blizzard_per_target",
                isPenalty = true,
            ),
            BlizzardBlankerRule,
        ),

        // ───────────── WILD (Jokers) ─────────────
        // shapeshifter / mirage / doppelganger — handled entirely by JokerResolver; no own rule

        // ───────────── WIZARD ─────────────
        // necromancer — phase-05 sandbox stub (no discard); produces no bonus, contributes only base strength 3
        "enchantress" to PerOtherCountRule(
            matcher = BySuit(Suit.LAND, Suit.WEATHER, Suit.FLOOD, Suit.FLAME),
            amountPer = 5,
            descriptionKey = "effect_enchantress_per_target",
        ),
        "collector" to CollectorRule,
        "beastmaster" to CompositeRule(
            PerOtherCountRule(
                matcher = BySuit(Suit.BEAST),
                amountPer = 9,
                descriptionKey = "effect_beastmaster_per_beast",
            ),
            BeastmasterCancelRule,
        ),
        "warlock" to PerOtherCountRule(
            matcher = BySuit(Suit.LEADER, Suit.WIZARD),
            amountPer = -10,
            descriptionKey = "effect_warlock_per_target",
            isPenalty = true,
        ),
    )
}
