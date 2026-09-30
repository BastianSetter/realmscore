package de.morzo.realmscore.domain.scoring.solver

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.domain.scoring.joker.JokerTargets

data class OptimalResult(
    val bestInput: ScoringInput,
    val bestResult: ScoringResult,
)

/**
 * Brute-force search over joker assignments × player choices (Island/Fountain/Necromancer/Angel).
 *
 * Necromancer pick (Phase 20): only brute-forced when [ScoringInput.discardScanned] is true. Then
 * the candidate set is the captured discard cards filtered to the Necromancer-eligible suits
 * (see [JokerTargets.necromancerSuits]), which is small enough to enumerate. Without a scanned
 * discard pile the candidate set would be every eligible card in the game — too large to search —
 * so the user's manual pick is carried through unchanged.
 *
 * All target cards are found by their [JokerType] rather than a fixed key, so the expansion's
 * replacement editions (`expansion_necromancer`, `expansion_fountain_of_life`, …) work the same way
 * (Phase 30). Mirage/Shapeshifter targets and Book-of-Changes suits come from the game's card pool.
 */
class OptimalSolver(
    private val engine: ScoringEngine,
    private val jokerResolver: JokerResolver,
    /** Card pool of a game: base game, or with the expansion's new suits (Phase 30). */
    private val cardPool: (newSuits: Boolean) -> List<CardDefinition>,
) {

    /** Fixed-pool convenience (tests). */
    constructor(engine: ScoringEngine, jokerResolver: JokerResolver, gameCards: List<CardDefinition>) :
        this(engine, jokerResolver, { _: Boolean -> gameCards })

    fun findOptimal(seed: ScoringInput): OptimalResult {
        val hand = seed.hand
        val handKeys = hand.map { it.key }.toSet()
        val pool = cardPool(seed.newSuits)
        // Book of Changes may pick any real suit of the game being played.
        val bookSuits = pool.map { it.suit }.distinct().filter { it != Suit.WILD }

        val island = JokerTargets.firstOfType(hand, JokerType.ISLAND)
        val fountain = JokerTargets.firstOfType(hand, JokerType.FOUNTAIN_OF_LIFE)
        val necromancer = JokerTargets.firstOfType(hand, JokerType.NECROMANCER)
        val angel = JokerTargets.firstOfType(hand, JokerType.ANGEL)

        // The Necromancer's pulled card is materialised as an extra resolved card, so it is also a
        // legal target for Book of Changes (re-suit the pulled card) and for Island/Fountain. Enumerate
        // the possible pull keys up front so they can be offered to Book of Changes. Only
        // mit-optimized when the middle was scanned (small candidate set → brute-force is sane);
        // otherwise the user's manual pick is the single candidate, carried through (spec 25.4).
        val necromancerPullKeys: List<String> = when {
            necromancer == null -> emptyList()
            seed.discardScanned -> {
                val suits = JokerTargets.necromancerSuits(necromancer.key)
                seed.discardPile
                    .filter { it.suit in suits && !it.isJoker && it.key !in handKeys }
                    .map { it.key }
            }
            else -> listOfNotNull(seed.jokerAssignments[necromancer.key]?.targetCardKey)
        }
        val necromancerOptions: List<JokerAssignment?> =
            listOf<JokerAssignment?>(null) +
                necromancerPullKeys.map { JokerAssignment(necromancer!!.key, it) }

        // Build per-joker target candidate lists (including "no assignment").
        val jokerCandidates = hand.mapNotNull { card ->
            if (!card.isJoker) return@mapNotNull null
            val candidates: List<JokerAssignment?> = when (card.jokerType) {
                JokerType.DOPPELGANGER -> listOf<JokerAssignment?>(null) +
                    hand.filter { it.key != card.key && !it.isJoker }
                        .map { JokerAssignment(card.key, it.key) }
                JokerType.MIRAGE, JokerType.SHAPESHIFTER -> {
                    val suits = JokerTargets.substitutionSuits(card)
                    listOf<JokerAssignment?>(null) +
                        pool.filter { it.suit in suits && !it.isJoker && it.key !in handKeys }
                            .map { JokerAssignment(card.key, it.key) }
                }
                JokerType.BOOK_OF_CHANGES -> {
                    // Hand cards plus any card the Necromancer might pull. A target that is not
                    // actually present in a given combo just resolves to a no-op.
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
                // Set the Necromancer pull first, then resolve, so the materialised card — and any
                // Book-of-Changes re-suit applied to it — is visible to the Island/Fountain/Angel
                // candidate lists below. Those candidates therefore come from the *resolved* hand of
                // THIS combo (e.g. a Doppelganger or a pulled card that has become a Flood is a valid
                // Island target). Each pick is written back as a JokerAssignment keyed by its card.
                val baseAssignments = jokerAssignments.toMutableMap()
                if (necroAssignment != null) baseAssignments[necroAssignment.jokerKey] = necroAssignment
                val resolved = jokerResolver.resolve(hand, baseAssignments)

                val islandOptions: List<String?> = if (island != null) {
                    listOf<String?>(null) + resolved
                        .filter { it.originalKey != island.key && it.effectiveSuit in JokerTargets.ISLAND_SUITS }
                        .map { it.originalKey }
                } else listOf(null)

                val fountainOptions: List<String?> = if (fountain != null) {
                    val suits = JokerTargets.fountainSuits(fountain.key)
                    listOf<String?>(null) + resolved
                        .filter {
                            it.originalKey != fountain.key &&
                                it.effectiveSuit in suits &&
                                it.effectiveStrength > 0
                        }
                        .map { it.originalKey }
                } else listOf(null)

                // Engel (Phase 30): protects any one other card of the resolved hand from blanking.
                val angelOptions: List<String?> = if (angel != null) {
                    listOf<String?>(null) + resolved.filter { it.originalKey != angel.key }.map { it.originalKey }
                } else listOf(null)

                for (islandTgt in islandOptions) {
                    for (fountainSrc in fountainOptions) {
                        for (angelTgt in angelOptions) {
                            val assignments = baseAssignments.toMutableMap()
                            if (island != null && islandTgt != null) {
                                assignments[island.key] = JokerAssignment(island.key, islandTgt)
                            }
                            if (fountain != null && fountainSrc != null) {
                                assignments[fountain.key] = JokerAssignment(fountain.key, fountainSrc)
                            }
                            if (angel != null && angelTgt != null) {
                                assignments[angel.key] = JokerAssignment(angel.key, angelTgt)
                            }
                            val candidateInput = seed.copy(jokerAssignments = assignments)
                            val result = engine.score(candidateInput)
                            val better = when {
                                best == null -> true
                                result.totalScore != best!!.bestResult.totalScore ->
                                    result.totalScore > best!!.bestResult.totalScore
                                // Equal score: prefer the combo that fills in more selections, so a
                                // pick whose effect is irrelevant still gets a valid value instead of
                                // being left "unset".
                                else -> candidateInput.selectionCount() > best!!.bestInput.selectionCount()
                            }
                            if (better) {
                                best = OptimalResult(candidateInput, result)
                            }
                        }
                    }
                }
            }
        }

        // No jokers? Still consider the seed via a single pass.
        if (best == null) {
            val result = engine.score(seed)
            best = OptimalResult(seed, result)
        }
        return best!!
    }

    /**
     * Number of filled-in selections — used to break score ties toward concrete values, so a
     * pick whose effect is irrelevant still gets a valid value instead of being left "unset".
     * All of them live in [jokerAssignments].
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
}
