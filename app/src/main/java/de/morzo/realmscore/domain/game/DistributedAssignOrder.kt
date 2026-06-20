package de.morzo.realmscore.domain.game

/**
 * Device-specific steal order for a distributed (multi-phone) round capture (Phase 28).
 *
 * **Round 1 / no prior data** (empty [priorSubmissions]): [ownFirst] first, then the deterministic
 * [globalOrder] (`[Mittelfeld?, hands by seatOrder]`) — i.e. the original Stage-B behaviour.
 *
 * **Round 2+**: each phone records (host-side) which units it submitted last round and in what order.
 * That per-device history seeds this round's priority:
 *  1. **Own list first** — the units *this* device (`myDeviceId`) submitted last round, in submit order,
 *     so a phone re-grabs "its" players first.
 *  2. **Then the combined list, walked from the back** — a round-robin interleave of every device's
 *     prior list (index 0 of every device, then index 1, … — devices ordered by id, so it is identical
 *     on every phone), **reversed**. The front of the combined list is everyone's own seat (position 0),
 *     which each phone re-grabs via its own-list priority; a *stealing* phone should instead take the
 *     leftover later-position hands first, so it walks the combined list in reverse. (This also matches
 *     the spec example: a phone whose own list is exhausted grabs the *last* combined entry first.)
 *     The interleave is by *position* within each device's list (submit order per device), not global time.
 *
 * The result is de-duplicated (first occurrence wins), filtered to the units that actually exist this
 * round ([globalOrder]); any unit present this round but absent from the seed (e.g. a player who joined
 * mid-game) is appended in [globalOrder] order. [forcedFirst] (the host's Mittelfeld) is always pinned
 * to the very front when non-null, preserving the Necromancer-correctness invariant.
 *
 * Pure and deterministic so it can be unit-tested in isolation; [RoundCaptureViewModel] supplies the
 * live inputs. The lock/done filtering that picks the *next* free unit still happens on top of this.
 */
object DistributedAssignOrder {

    fun build(
        priorSubmissions: Map<String, List<String>>,
        myDeviceId: String,
        globalOrder: List<String>,
        ownFirst: String?,
        forcedFirst: String?,
    ): List<String> {
        val present = globalOrder.toHashSet()

        val core: List<String> = if (priorSubmissions.isEmpty()) {
            listOfNotNull(ownFirst)
        } else {
            val ownList = priorSubmissions[myDeviceId].orEmpty()
            // Own list forward (re-grab my own hands in submit order); steal from the back of the
            // combined list (the leftover later-position hands), so phones don't fight over own seats.
            ownList + combinedRoundRobin(priorSubmissions).reversed()
        }

        // globalOrder tail backfills units missing from the seed (round 1: all of them; round 2+: new
        // players). filter keeps only this round's units; distinct collapses the natural duplicates.
        return (listOfNotNull(forcedFirst) + core + globalOrder)
            .filter { it in present }
            .distinct()
    }

    /** index 0 of every device (ids sorted), then index 1, … — the shared "combined" list. */
    private fun combinedRoundRobin(priorSubmissions: Map<String, List<String>>): List<String> {
        val deviceIds = priorSubmissions.keys.sorted()
        val maxLen = priorSubmissions.values.maxOfOrNull { it.size } ?: 0
        return buildList {
            for (i in 0 until maxLen) {
                for (deviceId in deviceIds) {
                    priorSubmissions[deviceId]?.getOrNull(i)?.let { add(it) }
                }
            }
        }
    }
}
