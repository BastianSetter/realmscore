package de.morzo.realmscore.ui.sandbox

sealed class SandboxLaunchData {
    data object Empty : SandboxLaunchData()
    data class FromRound(
        val gameId: String,
        val roundId: String,
        val profileId: String,
    ) : SandboxLaunchData()

    /** Open the Sandbox with a saved favorite (Phase 22) loaded into the slots. */
    data class FromFavorite(
        val favoriteId: String,
    ) : SandboxLaunchData()

    /**
     * Open with an in-memory hand snapshot already prepared (Phase 22). Used to pre-fill the left
     * column of the Multi-Hand compare view from the current Sandbox hand without round/DB access.
     */
    data class Prefilled(
        val snapshot: HandSnapshot,
    ) : SandboxLaunchData()
}
