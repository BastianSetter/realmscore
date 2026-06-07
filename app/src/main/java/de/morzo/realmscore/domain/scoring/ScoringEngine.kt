package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.blanking.BlankingResolver
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.domain.scoring.penalty.PenaltyContext
import de.morzo.realmscore.domain.scoring.rules.CardRuleRegistry

/**
 * Pure-Kotlin scoring pipeline. No Android imports.
 *
 * Order of operations (mirrors the rulebook: jokers → Book of Changes → clearing → penalties):
 *  1) Joker substitution (Doppelganger/Mirage/Shapeshifter → name+suit; Book of Changes → suit)
 *  2) Collect penalty cancellations from all rules (full pre-blanking hand). Clearing is
 *     permanent, so this is the final PenaltyContext — a blanked canceller still cleared.
 *  3) Blanking fixpoint (incl. self-blanks); a card whose penalty is cleared blanks nothing,
 *     and blanked sources lose their remaining effects
 *  4) Bonuses for non-blanked cards (bonusEnabled gate)
 *  5) Penalties for non-blanked cards (penaltyEnabled gate, PenaltyContext applied)
 *  6) Per-card totals = (base strength if not blanked) + bonus deltas + penalty deltas
 */
class ScoringEngine(
    private val registry: CardRuleRegistry,
    private val jokerResolver: JokerResolver,
    private val cardLookup: (String) -> CardDefinition?,
) {

    fun score(input: ScoringInput): ScoringResult {
        // 1) Joker auflösen
        val resolved = jokerResolver.resolve(input.hand, input.jokerAssignments)

        // Working context (no blanking, no penaltyContext yet)
        val workingCtx = ScoringContext(
            hand = resolved,
            blankedKeys = emptySet(),
            playerChoices = input.playerChoices,
            discardPile = input.discardPile,
            cardLookup = cardLookup,
            penaltyContext = null,
        )

        // 2) Cancellations sammeln (alle Karten, vor Blanking)
        // Cancellations are Boni ("hebt … auf") → only fire if bonusEnabled.
        val rawCancellations = resolved.flatMap { card ->
            if (!card.bonusEnabled) return@flatMap emptyList()
            val rule = registry.get(card.effectiveCardKey) ?: return@flatMap emptyList()
            rule.cancellations(card, workingCtx)
        }

        // 3) Blanking-Fixpunkt
        // Blanking is a Strafe → only fire if penaltyEnabled (Doppelganger copies penalties,
        // including blanking; Mirage/Shapeshifter inherit nothing of either kind). The resolver
        // also consumes the cancellations: a card whose penalty is fully cancelled (e.g. Herr
        // der Bestien → Basilisk, Gebirge → Große Flut) blanks nothing.
        val blanker = BlankingResolver { key -> registry.get(key) }
        val blankedKeys = blanker.resolve(workingCtx, rawCancellations)

        // 4) PenaltyContext: clearing is permanent.
        // Per the rulebook order of operations, clearing happens *before* penalties are applied,
        // so a canceller that is itself later blanked has still cleared its target's penalty
        // (e.g. Höhle clears Blizzard, then Große Flut drowns the Höhle — Blizzard stays cleared).
        // We therefore keep every collected cancellation; we do NOT drop blanked sources.
        val penaltyContext = PenaltyContext(rawCancellations)

        val finalCtx = workingCtx.copy(
            blankedKeys = blankedKeys,
            penaltyContext = penaltyContext,
        )

        // 5)+6) Boni & Strafen je Karte
        val perCard = resolved.map { card ->
            val isBlanked = card.originalKey in blankedKeys
            if (isBlanked) {
                return@map CardScoreResult(
                    cardKey = card.originalKey,
                    effectiveName = card.effectiveName,
                    contributedScore = 0,
                    isBlanked = true,
                    effects = emptyList(),
                )
            }

            val rule = registry.get(card.effectiveCardKey)
            val effects = mutableListOf<EffectApplication>()

            // Base strength as the first line item (skip jokers with strength 0)
            if (card.effectiveStrength != 0) {
                effects += EffectApplication(
                    sourceCardKey = card.originalKey,
                    descriptionKey = "effect_base_strength",
                    descriptionArgs = listOf(card.effectiveName),
                    pointsDelta = card.effectiveStrength,
                )
            }

            if (rule != null) {
                if (card.bonusEnabled) {
                    effects += rule.bonuses(card, finalCtx)
                }
                if (card.penaltyEnabled) {
                    effects += rule.penalties(card, finalCtx)
                }
            }

            CardScoreResult(
                cardKey = card.originalKey,
                effectiveName = card.effectiveName,
                contributedScore = effects.sumOf { it.pointsDelta },
                isBlanked = false,
                effects = effects,
            )
        }

        return ScoringResult(
            totalScore = perCard.sumOf { it.contributedScore },
            perCard = perCard,
            blankedKeys = blankedKeys,
        )
    }
}
