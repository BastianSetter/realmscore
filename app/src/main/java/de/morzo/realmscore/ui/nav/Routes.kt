package de.morzo.realmscore.ui.nav

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    // Footer-section subgraph routes. Each must differ from its startDestination route so the
    // nested navigation graph and its start leaf stay distinct destinations.
    const val SECTION_START = "section_start"
    const val SECTION_GAME = "section_game"
    const val SECTION_HISTORY = "section_history"
    const val SECTION_STATS = "section_stats"
    const val SECTION_SANDBOX = "section_sandbox"

    const val TAB_HOME = "tab_home"
    const val TAB_HISTORY = "tab_history"
    const val TAB_STATS = "tab_stats"
    const val TAB_SETTINGS = "tab_settings"

    const val GAME_HUB = "game_hub"

    const val NEW_GAME = "new_game?seedGameId={seedGameId}&continueSession={continueSession}"
    const val ARG_SEED_GAME_ID = "seedGameId"
    const val ARG_CONTINUE_SESSION = "continueSession"
    const val USERNAME_CHANGE = "settings/username_change"
    const val PROFILE_MANAGEMENT = "settings/profiles"
    const val SCAN_DEBUG = "settings/scan_debug"
    const val SANDBOX =
        "sandbox?launchType={launchType}&gameId={gameId}&roundId={roundId}" +
            "&profileId={profileId}&favoriteId={favoriteId}"
    const val ARG_LAUNCH_TYPE = "launchType"
    const val ARG_FAVORITE_ID = "favoriteId"
    const val SANDBOX_LAUNCH_EMPTY = "empty"
    const val SANDBOX_LAUNCH_FROM_ROUND = "fromRound"
    const val SANDBOX_LAUNCH_FROM_FAVORITE = "fromFavorite"
    const val SANDBOX_FAVORITES = "sandbox/favorites"

    // Phase 28 P2P sync.
    const val JOIN_SESSION = "p2p/join"
    const val NEW_GAME_WAIT = "p2p/new_game_wait"

    const val GAME_IN_PROGRESS = "game/{gameId}"
    const val ROUND_ENTRY = "round/{roundId}"
    const val ROUND_CAPTURE = "round/{roundId}/capture"
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

    fun newGameRoute(seedGameId: String = "", continueSession: Boolean = false): String =
        "new_game?seedGameId=$seedGameId&continueSession=$continueSession"

    fun gameRoute(gameId: String): String = "game/$gameId"
    fun roundEntryRoute(roundId: String): String = "round/$roundId"
    fun roundCaptureRoute(roundId: String): String = "round/$roundId/capture"
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
    fun sandboxRouteFromFavorite(favoriteId: String): String =
        "sandbox?launchType=$SANDBOX_LAUNCH_FROM_FAVORITE&favoriteId=$favoriteId"
    fun sandboxFavoritesRoute(): String = SANDBOX_FAVORITES
}
