package de.morzo.realmscore.domain.scoring.solver

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.PlayerChoices
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

    private val mirageTargets = gameCards.filter { it.suit in MIRAGE_SUITS && !it.isJoker }
    private val shapeshifterTargets = gameCards.filter { it.suit in SHAPESHIFTER_SUITS && !it.isJoker }
    private val bookSuits = Suit.entries.filter { it != Suit.WILD }

    fun findOptimal(seed: ScoringInput): OptimalResult {
        val hand = seed.hand
        val handKeys = hand.map { it.key }.toSet()

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
                JokerType.BOOK_OF_CHANGES -> listOf<JokerAssignment?>(null) +
                    hand.filter { it.key != card.key }
                        .flatMap { tgt ->
                            bookSuits.map { suit ->
                                JokerAssignment(card.key, tgt.key, targetSuit = suit)
                            }
                        }
                else -> listOf(null)
            }
            card.key to candidates
        }

        val islandPresent = hand.any { it.key == "island" }
        val fountainPresent = hand.any { it.key == "fountain_of_life" }
        val necromancerPresent = hand.any { it.key == NECROMANCER_KEY }

        // Necromancer candidates don't depend on joker assignments (they come from the discard
        // pile, never the hand), so enumerate them once. Only when the middle was scanned; else
        // keep the user's manual pick untouched.
        val necromancerOptions: List<String?> =
            if (necromancerPresent && seed.discardScanned) {
                listOf<String?>(null) + seed.discardPile
                    .filter { it.suit in NECROMANCER_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { it.key }
            } else {
                listOf(seed.playerChoices.necromancerPickKey)
            }

        var best: OptimalResult? = null

        forEachJokerCombo(jokerCandidates) { jokerAssignments ->
            val resolved = jokerResolver.resolve(hand, jokerAssignments)

            val islandOptions: List<String?> = if (islandPresent) {
                listOf<String?>(null) + resolved
                    .filter { it.originalKey != "island" && it.effectiveSuit in ISLAND_SUITS }
                    .map { it.originalKey }
            } else listOf(null)

            val fountainOptions: List<String?> = if (fountainPresent) {
                listOf<String?>(null) + resolved
                    .filter {
                        it.originalKey != "fountain_of_life" &&
                            it.effectiveSuit in FountainOfLifeRule.eligibleSuits &&
                            it.effectiveStrength > 0
                    }
                    .map { it.originalKey }
            } else listOf(null)

            for (islandTgt in islandOptions) {
                for (fountainSrc in fountainOptions) {
                    for (necroPick in necromancerOptions) {
                        val candidateInput = seed.copy(
                            jokerAssignments = jokerAssignments,
                            playerChoices = PlayerChoices(
                                islandTargetKey = islandTgt,
                                fountainSourceKey = fountainSrc,
                                necromancerPickKey = necroPick,
                            ),
                        )
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

    /** Number of filled-in selections — used to break score ties toward concrete values. */
    private fun ScoringInput.selectionCount(): Int =
        jokerAssignments.size +
            (if (playerChoices.islandTargetKey != null) 1 else 0) +
            (if (playerChoices.fountainSourceKey != null) 1 else 0) +
            (if (playerChoices.necromancerPickKey != null) 1 else 0)

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
        private val MIRAGE_SUITS = setOf(Suit.ARMY, Suit.LAND, Suit.WEATHER, Suit.FLOOD, Suit.FLAME)
        private val SHAPESHIFTER_SUITS = setOf(Suit.ARTIFACT, Suit.LEADER, Suit.WIZARD, Suit.WEAPON, Suit.BEAST)
        private val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)

        // Mirrors CardLookup.NECROMANCER_SUITS so "Optimal" offers the same candidates as the
        // manual Necromancer picker (Army/Wizard/Leader/Beast).
        private val NECROMANCER_SUITS = setOf(Suit.ARMY, Suit.WIZARD, Suit.LEADER, Suit.BEAST)
    }
}
