package de.morzo.realmscore.ui.sandbox

sealed class SandboxLaunchData {
    data object Empty : SandboxLaunchData()
    data class FromRound(
        val gameId: String,
        val roundId: String,
        val profileId: String,
    ) : SandboxLaunchData()
}
