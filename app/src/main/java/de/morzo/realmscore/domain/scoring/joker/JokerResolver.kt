package de.morzo.realmscore.domain.scoring.joker

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ResolvedCard

/**
 * Turns a (hand, jokerAssignments) pair into the post-substitution ResolvedCard list.
 *
 * Order of application is significant and follows the card-scoring order so that every joker sees the
 * state left by the previous ones (spec 25.4):
 *  1. Doppelganger/Mirage/Shapeshifter substitutions (they change WHICH card a slot effectively is).
 *  2. Necromancer pull: the chosen discard card is materialised as an extra 8th ResolvedCard, so it
 *     becomes visible to any later joker.
 *  3. Book of Changes overrides the suit of an already-resolved slot — including the materialised
 *     Necromancer pull (e.g. Necromancer pulls the Basilisk, Book turns it Beast→Flood, the Island
 *     then cancels its now-Flood penalty).
 */
class JokerResolver(
    private val gameCardLookup: (String) -> CardDefinition?,
) {

    fun resolve(
        hand: List<CardDefinition>,
        jokerAssignments: Map<String, JokerAssignment>,
    ): List<ResolvedCard> {
        // Step 0: initial ResolvedCard list, identity mapping.
        val resolved = hand.map { it.toResolved() }.toMutableList()

        // Step 1: name/suit/strength substitutions (everything except Book of Changes).
        // Island/Fountain/Necromancer carry a jokerType too, but isJoker is false (they keep their
        // own value), so they are skipped here and resolved separately (Island/Fountain as
        // scoring-time choices by their rules, Necromancer materialised below).
        for (i in hand.indices) {
            val original = hand[i]
            if (!original.isJoker || original.jokerType == JokerType.BOOK_OF_CHANGES) continue
            val assignment = jokerAssignments[original.key] ?: continue
            val targetKey = assignment.targetCardKey ?: continue
            val target = when (original.jokerType) {
                JokerType.DOPPELGANGER -> hand.firstOrNull { it.key == targetKey }
                JokerType.MIRAGE, JokerType.SHAPESHIFTER -> gameCardLookup(targetKey)
                else -> null
            } ?: continue

            resolved[i] = when (original.jokerType!!) {
                JokerType.DOPPELGANGER -> ResolvedCard(
                    originalKey = original.key,
                    effectiveCardKey = target.key,
                    effectiveSuit = target.suit,
                    effectiveStrength = target.baseStrength,
                    bonusEnabled = false,
                    penaltyEnabled = true,
                )
                JokerType.MIRAGE, JokerType.SHAPESHIFTER -> ResolvedCard(
                    originalKey = original.key,
                    effectiveCardKey = target.key,
                    effectiveSuit = target.suit,
                    effectiveStrength = 0,
                    bonusEnabled = false,
                    penaltyEnabled = false,
                )
                // Not reachable (gated out above) but kept for exhaustiveness.
                JokerType.BOOK_OF_CHANGES,
                JokerType.ISLAND,
                JokerType.FOUNTAIN_OF_LIFE,
                JokerType.NECROMANCER -> resolved[i]
            }
        }

        // Step 1b: Necromancer pull. The chosen discard card is appended as an extra ResolvedCard so
        // that later jokers (Book of Changes) and the scoring engine see it. Defensive: a joker-like
        // pull contributes base strength only and triggers NO nested substitution — it is never fed
        // back through this resolver, and its bonus/penalty rules are gated off when it is a joker.
        val necromancer = hand.firstOrNull { it.jokerType == JokerType.NECROMANCER }
        if (necromancer != null) {
            val picked = jokerAssignments[necromancer.key]?.targetCardKey?.let(gameCardLookup)
            if (picked != null) {
                resolved += ResolvedCard(
                    originalKey = picked.key,
                    effectiveCardKey = picked.key,
                    effectiveSuit = picked.suit,
                    effectiveStrength = picked.baseStrength,
                    bonusEnabled = !picked.isJoker,
                    penaltyEnabled = !picked.isJoker,
                )
            }
        }

        // Step 2: Book of Changes suit overrides — applied after substitution and the Necromancer
        // pull so it can re-suit any resolved slot, including the pulled 8th card, if the player wishes.
        for (i in hand.indices) {
            val original = hand[i]
            if (original.jokerType != JokerType.BOOK_OF_CHANGES) continue
            val assignment = jokerAssignments[original.key] ?: continue
            val targetKey = assignment.targetCardKey ?: continue
            val newSuit = assignment.targetSuit ?: continue

            val targetIdx = resolved.indexOfFirst { it.originalKey == targetKey }
            if (targetIdx < 0) continue
            resolved[targetIdx] = resolved[targetIdx].copy(effectiveSuit = newSuit)
        }

        return resolved
    }

    private fun CardDefinition.toResolved(): ResolvedCard = ResolvedCard(
        originalKey = key,
        effectiveCardKey = key,
        effectiveSuit = suit,
        effectiveStrength = baseStrength,
        bonusEnabled = !isJoker, // unassigned jokers contribute nothing
        penaltyEnabled = !isJoker,
    )
}
