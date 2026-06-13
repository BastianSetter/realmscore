package de.morzo.realmscore.domain.scoring.solver

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.domain.scoring.rules.specials.FountainOfLifeRule

data class OptimalResult(
    val bestInput: ScoringInput,
    val bestResult: ScoringResult,
)

/**
 * Brute-force search over joker assignments × player choices (Island/Fountain/Necromancer).
 *
 * Necromancer pick (Phase 20): only brute-forced when [ScoringInput.discardScanned] is true. Then
 * the candidate set is the captured discard cards filtered to the Necromancer-eligible suits
 * (mirrors CardLookup.NECROMANCER_SUITS), which is small enough to enumerate. Without a scanned
 * discard pile the candidate set would be every eligible card in the game — too large to search —
 * so the user's manual pick is carried through unchanged.
 */
class OptimalSolver(
    private val engine: ScoringEngine,
    private val jokerResolver: JokerResolver,
    private val gameCards: List<CardDefinition>,
) {

    private val mirageTargets = gameCards.filter { it.suit in JokerType.MIRAGE_SUITS && !it.isJoker }
    private val shapeshifterTargets =
        gameCards.filter { it.suit in JokerType.SHAPESHIFTER_SUITS && !it.isJoker }
    private val bookSuits = Suit.entries.filter { it != Suit.WILD }

    fun findOptimal(seed: ScoringInput): OptimalResult {
        val hand = seed.hand
        val handKeys = hand.map { it.key }.toSet()

        val islandPresent = hand.any { it.key == ISLAND_KEY }
        val fountainPresent = hand.any { it.key == FOUNTAIN_KEY }
        val necromancerPresent = hand.any { it.key == NECROMANCER_KEY }

        // The Necromancer's pulled card is materialised as an extra resolved card, so it is also a
        // legal target for Book of Changes (re-suit the 8th card) and for Island/Fountain. Enumerate
        // the possible pull keys up front so they can be offered to Book of Changes. Only
        // mit-optimized when the middle was scanned (small candidate set → brute-force is sane);
        // otherwise the user's manual pick is the single candidate, carried through (spec 25.4).
        val necromancerPullKeys: List<String> =
            if (necromancerPresent && seed.discardScanned) {
                seed.discardPile
                    .filter { it.suit in NECROMANCER_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { it.key }
            } else {
                listOfNotNull(seed.jokerAssignments[NECROMANCER_KEY]?.targetCardKey)
            }
        val necromancerOptions: List<JokerAssignment?> =
            listOf<JokerAssignment?>(null) +
                necromancerPullKeys.map { JokerAssignment(NECROMANCER_KEY, it) }

        // Build per-joker target candidate lists (including "no assignment").
        val jokerCandidates = hand.mapNotNull { card ->
            if (!card.isJoker) return@mapNotNull null
            val candidates: List<JokerAssignment?> = when (card.jokerType) {
                JokerType.DOPPELGANGER -> listOf<JokerAssignment?>(null) +
                    hand.filter { it.key != card.key && !it.isJoker }
                        .map { JokerAssignment(card.key, it.key) }
                JokerType.MIRAGE -> listOf<JokerAssignment?>(null) +
                    mirageTargets.filter { it.key !in handKeys }
                        .map { JokerAssignment(card.key, it.key) }
                JokerType.SHAPESHIFTER -> listOf<JokerAssignment?>(null) +
                    shapeshifterTargets.filter { it.key !in handKeys }
                        .map { JokerAssignment(card.key, it.key) }
                JokerType.BOOK_OF_CHANGES -> {
                    // Hand cards plus any card the Necromancer might pull (the 8th card). A target
                    // that is not actually present in a given combo just resolves to a no-op.
                    val targetKeys = hand.filter { it.key != card.key }.map { it.key } +
                        necromancerPullKeys
                    listOf<JokerAssignment?>(null) + targetKeys.flatMap { tgtKey ->
                        bookSuits.map { suit -> JokerAssignment(card.key, tgtKey, targetSuit = suit) }
                    }
                }
                else -> listOf(null)
            }
            card.key to candidates
        }

        var best: OptimalResult? = null

        forEachJokerCombo(jokerCandidates) { jokerAssignments ->
            for (necroAssignment in necromancerOptions) {
                // Set the Necromancer pull first, then resolve, so the materialised 8th card — and any
                // Book-of-Changes re-suit applied to it — is visible to the Island/Fountain candidate
                // lists below. Island/Fountain candidates therefore come from the *resolved* hand of
                // THIS combo (e.g. a Doppelganger or a pulled card that has become a Flood is a valid
                // Island target). Each pick is written back as a JokerAssignment keyed by its card.
                val baseAssignments = jokerAssignments.toMutableMap()
                if (necroAssignment != null) baseAssignments[NECROMANCER_KEY] = necroAssignment
                val resolved = jokerResolver.resolve(hand, baseAssignments)

                val islandOptions: List<String?> = if (islandPresent) {
                    listOf<String?>(null) + resolved
                        .filter { it.originalKey != ISLAND_KEY && it.effectiveSuit in ISLAND_SUITS }
                        .map { it.originalKey }
                } else listOf(null)

                val fountainOptions: List<String?> = if (fountainPresent) {
                    listOf<String?>(null) + resolved
                        .filter {
                            it.originalKey != FOUNTAIN_KEY &&
                                it.effectiveSuit in FountainOfLifeRule.eligibleSuits &&
                                it.effectiveStrength > 0
                        }
                        .map { it.originalKey }
                } else listOf(null)

                for (islandTgt in islandOptions) {
                    for (fountainSrc in fountainOptions) {
                        val assignments = baseAssignments.toMutableMap()
                        if (islandTgt != null) {
                            assignments[ISLAND_KEY] = JokerAssignment(ISLAND_KEY, islandTgt)
                        }
                        if (fountainSrc != null) {
                            assignments[FOUNTAIN_KEY] = JokerAssignment(FOUNTAIN_KEY, fountainSrc)
                        }
                        val candidateInput = seed.copy(jokerAssignments = assignments)
                        val result = engine.score(candidateInput)
                        val better = when {
                            best == null -> true
                            result.totalScore != best!!.bestResult.totalScore ->
                                result.totalScore > best!!.bestResult.totalScore
                            // Equal score: prefer the combo that fills in more selections, so a
                            // joker / Island / Fountain / Necromancer pick whose effect is
                            // irrelevant still gets a valid value instead of being left "unset".
                            else -> candidateInput.selectionCount() > best!!.bestInput.selectionCount()
                        }
                        if (better) {
                            best = OptimalResult(candidateInput, result)
                        }
                    }
                }
            }
        }

        // No jokers? Still consider Island/Fountain choices alone via a single pass.
        if (best == null) {
            val result = engine.score(seed)
            best = OptimalResult(seed, result)
        }
        return best!!
    }

    /**
     * Number of filled-in selections — used to break score ties toward concrete values, so a
     * joker / Island / Fountain / Necromancer pick whose effect is irrelevant still gets a valid
     * value instead of being left "unset". All of them now live in [jokerAssignments].
     */
    private fun ScoringInput.selectionCount(): Int = jokerAssignments.size

    private inline fun forEachJokerCombo(
        candidates: List<Pair<String, List<JokerAssignment?>>>,
        crossinline action: (Map<String, JokerAssignment>) -> Unit,
    ) {
        if (candidates.isEmpty()) {
            action(emptyMap())
            return
        }
        val indices = IntArray(candidates.size)
        val sizes = candidates.map { it.second.size }.toIntArray()
        while (true) {
            val map = mutableMapOf<String, JokerAssignment>()
            for (i in candidates.indices) {
                val a = candidates[i].second[indices[i]]
                if (a != null) map[candidates[i].first] = a
            }
            action(map)
            // increment
            var carry = true
            for (i in candidates.indices) {
                if (!carry) break
                indices[i]++
                if (indices[i] >= sizes[i]) {
                    indices[i] = 0
                } else {
                    carry = false
                }
            }
            if (carry) return
        }
    }

    companion object {
        private const val NECROMANCER_KEY = "necromancer"
        private const val ISLAND_KEY = "island"
        private const val FOUNTAIN_KEY = "fountain_of_life"
        private val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)

        // Mirrors CardLookup.NECROMANCER_SUITS so "Optimal" offers the same candidates as the
        // manual Necromancer picker (Army/Wizard/Leader/Beast).
        private val NECROMANCER_SUITS = setOf(Suit.ARMY, Suit.WIZARD, Suit.LEADER, Suit.BEAST)
    }
}
