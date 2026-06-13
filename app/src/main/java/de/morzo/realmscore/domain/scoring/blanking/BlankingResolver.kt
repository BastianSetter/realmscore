package de.morzo.realmscore.domain.scoring.blanking

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext
import de.morzo.realmscore.domain.scoring.penalty.PenaltyCancellation
import de.morzo.realmscore.domain.scoring.penalty.PenaltyContext

/**
 * Computes which originalKeys are blanked, by fixpoint iteration.
 *
 * A blank effect IS the source card's penalty ("Strafe: blockiert …"). Two consequences:
 *
 *   • SelfBlank ("ist blockiert wenn …") — the source card blanks itself if its condition
 *     holds on the **currently-active** (non-blanked) hand. A self-blanker still self-blanks
 *     even if it's already in the blanked set (otherwise we oscillate).
 *   • Blank-others (BlankBySuit / BlankBySuitExcept / BlankAllExcept) — the source only
 *     emits these while it is itself **not** blanked.
 *
 * Because blanking is a penalty, a card whose penalty is fully cleared blanks **nothing**:
 *   Herr der Bestien → Basilisk (Beast), Gebirge → Große Flut (Flood), Höhle → Blizzard
 *   (Weather), Rune des Schutzes → everything, Insel → its chosen Flood/Flame.
 *
 * Per the rulebook order of operations, clearing happens *before* any penalty is applied and
 * is permanent: a card that clears a penalty and is then itself blanked has still successfully
 * cleared it (e.g. Höhle clears Blizzard, then Große Flut drowns the Höhle — the Blizzard stays
 * cleared). The cancellation snapshot is therefore fixed for the whole fixpoint, which also
 * means clearing can never re-enable a blanker mid-loop, so the loop still converges quickly.
 *
 * Base-game cycles are impossible, so the loop converges within a few rounds; we cap at 10.
 */
/**
 * Result of a blanking fixpoint: the set of blanked originalKeys plus, per blanked card, the
 * other cards responsible (self-blanks excluded).
 */
data class BlankingOutcome(
    val blanked: Set<String>,
    val blankedBy: Map<String, List<String>>,
)

class BlankingResolver(
    private val ruleFor: (String) -> CardScoringRule?,
) {

    /**
     * @param rawCancellations all penalty cancellations emitted by the hand (bonus-gated,
     *        collected from the full pre-blanking hand). Clearing is permanent, so this snapshot
     *        is held constant; any blanker whose own penalty it fully cancels blanks nothing.
     * @return the blanked originalKeys together with, per blanked card, the *other* cards that
     *         blank it (self-blanks excluded from the source list).
     */
    fun resolve(
        initialContext: ScoringContext,
        rawCancellations: List<PenaltyCancellation> = emptyList(),
    ): BlankingOutcome {
        val hand = initialContext.hand
        val penaltyCtx = PenaltyContext(rawCancellations)
        var blanked: Set<String> = emptySet()
        var lastMap: Map<String, List<String>> = emptyMap()
        repeat(MAX_ROUNDS) {
            val ctxThisRound = initialContext.copy(
                blankedKeys = blanked,
                penaltyContext = penaltyCtx,
            )
            val newMap = computeBlanked(hand, ctxThisRound, penaltyCtx)
            lastMap = newMap.mapValues { it.value.toList() }
            val newBlanked = newMap.keys
            // The blanked set is the fixpoint variable; once it stabilizes the (deterministic)
            // source map computed against it is final too.
            if (newBlanked == blanked) return BlankingOutcome(blanked, lastMap)
            blanked = LinkedHashSet(newBlanked)
        }
        return BlankingOutcome(blanked, lastMap)
    }

    private fun computeBlanked(
        hand: List<ResolvedCard>,
        ctx: ScoringContext,
        penaltyCtx: PenaltyContext,
    ): Map<String, MutableSet<String>> {
        // Pass conditions a hand that doesn't include already-blanked cards.
        val activeHand = hand.filter { it.originalKey !in ctx.blankedKeys }
        // target originalKey -> originalKeys of the foreign cards blanking it (self excluded).
        val result = LinkedHashMap<String, MutableSet<String>>()
        for (target in hand) {
            for (source in hand) {
                if (!source.penaltyEnabled) continue
                // Blanking is the source's penalty; a fully-cleared penalty blanks nothing.
                if (penaltyCtx.isFullyCancelled(source)) continue
                val rule = ruleFor(source.effectiveCardKey) ?: continue
                val effects = rule.blanking(source, ctx)
                for (effect in effects) {
                    val isSelf = effect is BlankingEffect.SelfBlank
                    // Blank-others effects from a blanked source are inert.
                    if (!isSelf && source.originalKey in ctx.blankedKeys) continue
                    if (effect.blanks(self = source, target = target, hand = activeHand)) {
                        val sources = result.getOrPut(target.originalKey) { linkedSetOf() }
                        // A self-blank marks the card blanked but is not a foreign "blocked by".
                        if (source.originalKey != target.originalKey) {
                            sources += source.originalKey
                        }
                        break
                    }
                }
            }
        }
        return result
    }

    companion object {
        private const val MAX_ROUNDS = 10
    }
}
