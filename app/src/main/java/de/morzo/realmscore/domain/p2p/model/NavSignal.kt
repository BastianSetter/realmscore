package de.morzo.realmscore.domain.p2p.model

/**
 * One-shot navigation command the [de.morzo.realmscore.domain.p2p.P2PSessionRepository] emits so the
 * UI can follow the host through the shared game flow (Phase 28, Stage B). Delivered over a
 * `SharedFlow` (not state): each signal fires the corresponding navigation exactly once.
 */
sealed interface NavSignal {

    /** Enter (or move to) the round-capture screen for [roundId] — host started a round. */
    data class OpenRound(val roundId: String) : NavSignal

    /** Open the reveal for [roundId] — the host has computed and broadcast the round's results. */
    data class OpenReveal(val roundId: String) : NavSignal
}
