package de.morzo.realmscore.data.p2p

import de.morzo.realmscore.domain.p2p.model.DeviceLock

/**
 * Host-authoritative optimistic-lock registry for distributed round capture (Phase 28, Stage B). Only
 * the host owns an instance: clients ask for locks over Bluetooth and the host arbitrates here, so the
 * assignment of player hands / Mittelfeld to devices is race-free. A "unit" is a player profile id or
 * the Mittelfeld sentinel; the key is `roundId:unitId` (see [DeviceLock.key]).
 *
 * A unit moves lock → done on submit and is then never re-lockable (a finished hand is not reassigned
 * in the MVP). [releaseAllHeldBy] backs the heartbeat auto-reclaim (B6); [forceUnlock] backs the manual
 * "Übernehmen" (B3).
 */
class LockManager {

    private val locks = LinkedHashMap<String, String>() // "roundId:unitId" -> deviceId
    private val done = LinkedHashSet<String>()           // "roundId:unitId"

    /** Grants the lock if the unit is free or already held by [deviceId]; false if held by another or done. */
    @Synchronized
    fun tryLock(roundId: String, unitId: String, deviceId: String): Boolean {
        val key = DeviceLock.key(roundId, unitId)
        if (key in done) return false
        val holder = locks[key]
        if (holder != null && holder != deviceId) return false
        locks[key] = deviceId
        return true
    }

    /** Releases the lock only if [deviceId] currently holds it. */
    @Synchronized
    fun release(roundId: String, unitId: String, deviceId: String) {
        val key = DeviceLock.key(roundId, unitId)
        if (locks[key] == deviceId) locks.remove(key)
    }

    /** Unconditionally drops the lock (manual "Übernehmen" / a client's own release). */
    @Synchronized
    fun forceUnlock(roundId: String, unitId: String) {
        locks.remove(DeviceLock.key(roundId, unitId))
    }

    /** Marks the unit finished and drops its lock; it can no longer be locked. */
    @Synchronized
    fun markDone(roundId: String, unitId: String) {
        val key = DeviceLock.key(roundId, unitId)
        locks.remove(key)
        done += key
    }

    /** Frees every lock held by a (disconnected) device so its units return to the pool (B6). */
    @Synchronized
    fun releaseAllHeldBy(deviceId: String) {
        locks.entries.removeAll { it.value == deviceId }
    }

    /** The current lock + done state for one round (used to build the broadcast [SyncMessage.RoundStatus]). */
    @Synchronized
    fun snapshot(roundId: String): LockSnapshot {
        val prefix = "$roundId:"
        return LockSnapshot(
            locks = locks.filterKeys { it.startsWith(prefix) }
                .map { (key, deviceId) -> key.removePrefix(prefix) to deviceId },
            doneUnitIds = done.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) },
        )
    }

    @Synchronized
    fun reset() {
        locks.clear()
        done.clear()
    }
}

/** Immutable view of [LockManager] state for one round: held locks (unitId → deviceId) and finished units. */
data class LockSnapshot(
    val locks: List<Pair<String, String>>,
    val doneUnitIds: List<String>,
)
