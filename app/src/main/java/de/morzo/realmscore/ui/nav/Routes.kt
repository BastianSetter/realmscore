package de.morzo.realmscore.ui.nav

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    const val TAB_HOME = "tab_home"
    const val TAB_HISTORY = "tab_history"
    const val TAB_STATS = "tab_stats"
    const val TAB_SETTINGS = "tab_settings"

    const val NEW_GAME = "new_game"
    const val USERNAME_CHANGE = "settings/username_change"
    const val PROFILE_MANAGEMENT = "settings/profiles"
    const val SANDBOX =
        "sandbox?launchType={launchType}&gameId={gameId}&roundId={roundId}&profileId={profileId}"
    const val ARG_LAUNCH_TYPE = "launchType"
    const val SANDBOX_LAUNCH_EMPTY = "empty"
    const val SANDBOX_LAUNCH_FROM_ROUND = "fromRound"

    const val GAME_IN_PROGRESS = "game/{gameId}"
    const val ROUND_ENTRY = "round/{roundId}"
    const val PLAYER_HAND_ENTRY = "round/{roundId}/player/{profileId}"
    const val REVEAL = "round/{roundId}/reveal"
    const val ROUND_SUMMARY = "round/{roundId}/summary"
    const val GAME_SUMMARY = "game/{gameId}/summary"

    const val PLAYER_STATS = "stats/player/{profileId}"
    const val CARD_STATS_OVERVIEW = "stats/cards"
    const val CARD_STATS = "stats/card/{cardKey}"
    const val HEAD_TO_HEAD = "stats/h2h/{profileIdA}/{profileIdB}"

    const val ARG_GAME_ID = "gameId"
    const val ARG_ROUND_ID = "roundId"
    const val ARG_PROFILE_ID = "profileId"
    const val ARG_PROFILE_ID_A = "profileIdA"
    const val ARG_PROFILE_ID_B = "profileIdB"
    const val ARG_CARD_KEY = "cardKey"

    fun gameRoute(gameId: String): String = "game/$gameId"
    fun roundEntryRoute(roundId: String): String = "round/$roundId"
    fun playerHandEntryRoute(roundId: String, profileId: String): String =
        "round/$roundId/player/$profileId"
    fun revealRoute(roundId: String): String = "round/$roundId/reveal"
    fun roundSummaryRoute(roundId: String): String = "round/$roundId/summary"
    fun gameSummaryRoute(gameId: String): String = "game/$gameId/summary"

    fun playerStatsRoute(profileId: String): String = "stats/player/$profileId"
    fun cardStatsRoute(cardKey: String): String = "stats/card/$cardKey"
    fun cardStatsOverviewRoute(): String = CARD_STATS_OVERVIEW
    fun headToHeadRoute(profileIdA: String, profileIdB: String): String =
        "stats/h2h/$profileIdA/$profileIdB"

    fun sandboxRouteEmpty(): String = "sandbox"
    fun sandboxRouteFromRound(gameId: String, roundId: String, profileId: String): String =
        "sandbox?launchType=$SANDBOX_LAUNCH_FROM_ROUND" +
            "&gameId=$gameId&roundId=$roundId&profileId=$profileId"
}
