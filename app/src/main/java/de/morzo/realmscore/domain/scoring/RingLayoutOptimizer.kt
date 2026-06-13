package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition
import kotlin.math.abs

/**
 * Arranges hand cards around a ring so that strongly connected cards sit close together.
 *
 * Strategy (Phase 18 spec):
 *  1. The highest-scoring card is anchored at position 0 (12 o'clock).
 *  2. The remaining positions are filled by brute-forcing all permutations of the rest
 *     (6! = 720 for a normal 7-card hand; up to 7! = 5040 once the Necromancer adds an 8th card).
 *  3. A permutation's cost is `Σ |weight| × arcDist(posSource, posTarget)` over all connections,
 *     where `arcDist` is the smallest hop count between two ring positions.
 *  4. The minimum-cost permutation wins; ties break on the lexicographically smallest sequence of
 *     card keys for determinism.
 */
object RingLayoutOptimizer {

    /**
     * @param cards the hand cards.
     * @param scores `contributedScore` parallel to [cards]; used only to pick the top anchor.
     * @param connections influence edges referencing indices into [cards].
     * @return an ordering of card indices where `result[0]` is the top (12 o'clock) position.
     */
    fun optimize(
        cards: List<CardDefinition>,
        scores: List<Int>,
        connections: List<RingConnection>,
    ): List<Int> {
        val n = cards.size
        if (n <= 1) return cards.indices.toList()

        // Anchor: highest score, ties broken by alphabetically smallest key.
        val anchor = cards.indices.maxWith(
            compareBy<Int> { scores.getOrElse(it) { 0 } }.thenByDescending { cards[it].key },
        )

        val rest = cards.indices.filter { it != anchor }

        var bestOrder: List<Int>? = null
        var bestCost = Int.MAX_VALUE
        var bestKeys: List<String>? = null

        permutations(rest) { perm ->
            val order = ArrayList<Int>(n).apply {
                add(anchor)
                addAll(perm)
            }
            // pos[cardIdx] = ring position of that card
            val pos = IntArray(n)
            order.forEachIndexed { position, cardIdx -> pos[cardIdx] = position }

            var cost = 0
            for (conn in connections) {
                cost += abs(conn.weight) * arcDist(pos[conn.fromCardIdx], pos[conn.toCardIdx], n)
            }

            if (cost < bestCost) {
                bestCost = cost
                bestOrder = order
                bestKeys = order.map { cards[it].key }
            } else if (cost == bestCost) {
                val keys = order.map { cards[it].key }
                if (lexLess(keys, bestKeys!!)) {
                    bestOrder = order
                    bestKeys = keys
                }
            }
        }

        return bestOrder ?: cards.indices.toList()
    }

    /** Smallest hop count between two positions on a ring of [n] slots (0 .. n/2). */
    private fun arcDist(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b)
        return minOf(d, n - d)
    }

    private fun lexLess(a: List<String>, b: List<String>): Boolean {
        for (i in a.indices) {
            val cmp = a[i].compareTo(b[i])
            if (cmp != 0) return cmp < 0
        }
        return false
    }

    /** Visits every permutation of [items] without allocating the full 720-element list. */
    private fun permutations(items: List<Int>, visit: (List<Int>) -> Unit) {
        val arr = items.toIntArray()
        permute(arr, 0, visit)
    }

    private fun permute(arr: IntArray, k: Int, visit: (List<Int>) -> Unit) {
        if (k == arr.size) {
            visit(arr.toList())
            return
        }
        for (i in k until arr.size) {
            swap(arr, k, i)
            permute(arr, k + 1, visit)
            swap(arr, k, i)
        }
    }

    private fun swap(arr: IntArray, i: Int, j: Int) {
        val t = arr[i]; arr[i] = arr[j]; arr[j] = t
    }
}
