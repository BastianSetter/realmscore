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
 *  1) Joker resolution (Doppelganger/Mirage/Shapeshifter → name+suit; Necromancer materialises the
 *     pulled discard card as an extra 8th card; Book of Changes → suit, applied last so it can
 *     re-suit even the pulled card). All of this happens in the JokerResolver, in calculation order
 *     so each joker sees the previous ones. The 8th card interacts fully with the hand (counts for
 *     per-suit bonuses, can be blanked, can blank).
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
        // 1) Joker auflösen — inkl. Totenbeschwörer-Pull (8. Karte) und Buch der Veränderung, alles
        // in Berechnungsreihenfolge im JokerResolver.
        val resolved = jokerResolver.resolve(input.hand, input.jokerAssignments)

        // Working context (no blanking, no penaltyContext yet)
        val workingCtx = ScoringContext(
            hand = resolved,
            blankedKeys = emptySet(),
            discardPile = input.discardPile,
            cardLookup = cardLookup,
            jokerAssignments = input.jokerAssignments,
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
        val blankingOutcome = blanker.resolve(workingCtx, rawCancellations)
        val blankedKeys = blankingOutcome.blanked

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

        // The Necromancer's pulled card is the only resolved card whose originalKey is not a hand
        // card; flag it for the breakdown UI. Guard against a stale pick when no Necromancer is held.
        val necromancerPickKey = input.jokerAssignments[NECROMANCER_KEY]?.targetCardKey
            ?.takeIf { input.hand.any { c -> c.key == NECROMANCER_KEY } }

        // 5)+6) Boni & Strafen je Karte
        val perCard = resolved.map { card ->
            val isNecromancerPick = card.originalKey == necromancerPickKey
            val isBlanked = card.originalKey in blankedKeys
            // Book of Changes re-suits a card while it keeps its own identity (effectiveCardKey ==
            // originalKey). A different effectiveCardKey means a Doppelganger/Mirage/Shapeshifter
            // substitution instead, which is not a suit-only change and must not be flagged here.
            val printedSuit = cardLookup(card.originalKey)?.suit
            val bookOfChangesSuit = card.effectiveSuit.takeIf {
                card.effectiveCardKey == card.originalKey && printedSuit != null && it != printedSuit
            }
            if (isBlanked) {
                return@map CardScoreResult(
                    cardKey = card.originalKey,
                    effectiveCardKey = card.effectiveCardKey,
                    contributedScore = 0,
                    isBlanked = true,
                    effects = emptyList(),
                    isNecromancerPick = isNecromancerPick,
                    bookOfChangesSuit = bookOfChangesSuit,
                )
            }

            val rule = registry.get(card.effectiveCardKey)
            val effects = mutableListOf<EffectApplication>()

            // Base strength as the first line item (skip jokers with strength 0)
            if (card.effectiveStrength != 0) {
                effects += EffectApplication(
                    sourceCardKey = card.originalKey,
                    descriptionKey = "effect_base_strength",
                    nameCardKey = card.effectiveCardKey,
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
                effectiveCardKey = card.effectiveCardKey,
                contributedScore = effects.sumOf { it.pointsDelta },
                isBlanked = false,
                effects = effects,
                isNecromancerPick = isNecromancerPick,
                bookOfChangesSuit = bookOfChangesSuit,
            )
        }

        return ScoringResult(
            totalScore = perCard.sumOf { it.contributedScore },
            perCard = perCard,
            blankedKeys = blankedKeys,
            blankedBy = blankingOutcome.blankedBy,
        )
    }

    private companion object {
        const val NECROMANCER_KEY = "necromancer"
    }
}
