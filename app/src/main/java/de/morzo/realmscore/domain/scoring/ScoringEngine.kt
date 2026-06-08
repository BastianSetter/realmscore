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
 *  1b) Necromancer pick: if the hand holds a Necromancer and an eligible discard card was chosen
 *      (Army/Wizard/Leader/Beast), append it as an extra 8th card BEFORE bonuses/penalties.
 *      It interacts fully with the hand (counts for per-suit bonuses, can be blanked, can blank).
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
        val jokerResolved = jokerResolver.resolve(input.hand, input.jokerAssignments)

        // 1b) Totenbeschwörer: geholte Karte als zusätzliche (8.) Karte einfügen.
        // Nur wenn ein Totenbeschwörer in der Hand liegt und eine Karte gewählt wurde.
        val resolved = jokerResolved + buildNecromancerPick(input)

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

        val necromancerPickKey = effectiveNecromancerPickKey(input)

        // 5)+6) Boni & Strafen je Karte
        val perCard = resolved.map { card ->
            val isNecromancerPick = card.originalKey == necromancerPickKey
            val isBlanked = card.originalKey in blankedKeys
            if (isBlanked) {
                return@map CardScoreResult(
                    cardKey = card.originalKey,
                    effectiveName = card.effectiveName,
                    contributedScore = 0,
                    isBlanked = true,
                    effects = emptyList(),
                    isNecromancerPick = isNecromancerPick,
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
                isNecromancerPick = isNecromancerPick,
            )
        }

        return ScoringResult(
            totalScore = perCard.sumOf { it.contributedScore },
            perCard = perCard,
            blankedKeys = blankedKeys,
        )
    }

    /**
     * The Necromancer's pulled card, resolved as a plain extra card (or empty if not applicable).
     *
     * Defensive modelling per spec: the pulled card is scored with its BASE effect only. The UI
     * already restricts picks to Army/Wizard/Leader/Beast suits, so WILD jokers
     * (Mirage/Shapeshifter/Doppelganger) can't be chosen — but if a joker-like card were ever fed
     * in, it contributes only its base strength and triggers NO nested joker selection (no "joker
     * pulls joker" recursion). Concretely we never feed it back through the JokerResolver and gate
     * its bonus/penalty rules off when it is itself a joker.
     */
    private fun buildNecromancerPick(input: ScoringInput): List<ResolvedCard> {
        val pickKey = effectiveNecromancerPickKey(input) ?: return emptyList()
        val picked = cardLookup(pickKey) ?: return emptyList()
        // Pulled card is from the discard pile, so its key never collides with a hand key.
        return listOf(
            ResolvedCard(
                originalKey = picked.key,
                effectiveCardKey = picked.key,
                effectiveName = picked.nameDe,
                effectiveSuit = picked.suit,
                effectiveStrength = picked.baseStrength,
                bonusEnabled = !picked.isJoker,
                penaltyEnabled = !picked.isJoker,
            )
        )
    }

    /**
     * The chosen pick key, but only honored when a Necromancer is actually in the hand. Returns
     * null if there is no Necromancer (so a stale pick from a prior hand can't leak into scoring).
     */
    private fun effectiveNecromancerPickKey(input: ScoringInput): String? {
        val pickKey = input.playerChoices.necromancerPickKey ?: return null
        if (input.hand.none { it.key == NECROMANCER_KEY }) return null
        return pickKey
    }

    private companion object {
        const val NECROMANCER_KEY = "necromancer"
    }
}
