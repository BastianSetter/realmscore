package de.morzo.realmscore.domain.game

import de.morzo.realmscore.domain.model.GameParticipant

/**
 * Default player order for the Phase 18.1 round capture: replay the previous round's scan order
 * ([GameParticipant.lastScanOrder] ascending); brand-new players without a recorded scan order go
 * last, ordered by [GameParticipant.seatOrder].
 */
object CaptureOrdering {
    fun order(participants: List<GameParticipant>): List<GameParticipant> =
        participants.sortedWith(
            compareBy(
                { it.lastScanOrder == null },
                { it.lastScanOrder ?: Int.MAX_VALUE },
                { it.seatOrder },
            ),
        )
}
