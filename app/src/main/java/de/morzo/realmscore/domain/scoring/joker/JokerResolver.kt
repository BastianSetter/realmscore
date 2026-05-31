package de.morzo.realmscore.domain.scoring.joker

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ResolvedCard

/**
 * Turns a (hand, jokerAssignments) pair into the post-substitution ResolvedCard list.
 *
 * Order of application is significant: Doppelganger/Mirage/Shapeshifter substitutions happen
 * first (they change WHICH card a slot effectively is), then Book of Changes overrides the
 * suit of an already-resolved slot.
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
        for (i in resolved.indices) {
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
                    effectiveName = target.nameDe,
                    effectiveSuit = target.suit,
                    effectiveStrength = target.baseStrength,
                    bonusEnabled = false,
                    penaltyEnabled = true,
                )
                JokerType.MIRAGE, JokerType.SHAPESHIFTER -> ResolvedCard(
                    originalKey = original.key,
                    effectiveCardKey = target.key,
                    effectiveName = target.nameDe,
                    effectiveSuit = target.suit,
                    effectiveStrength = 0,
                    bonusEnabled = false,
                    penaltyEnabled = false,
                )
                JokerType.BOOK_OF_CHANGES -> resolved[i] // handled in step 2
            }
        }

        // Step 2: Book of Changes suit overrides — applied after substitution so it can
        // re-suit a Doppelganger/Mirage/Shapeshifter outcome if the player wishes.
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
        effectiveName = nameDe,
        effectiveSuit = suit,
        effectiveStrength = baseStrength,
        bonusEnabled = !isJoker, // unassigned jokers contribute nothing
        penaltyEnabled = !isJoker,
    )
}
