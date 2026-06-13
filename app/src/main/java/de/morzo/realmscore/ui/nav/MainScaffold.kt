package de.morzo.realmscore.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.game.GameInProgressScreen
import de.morzo.realmscore.ui.game.RoundCaptureScreen
import de.morzo.realmscore.ui.game.RoundEntryScreen
import de.morzo.realmscore.ui.handentry.PlayerHandEntryScreen
import de.morzo.realmscore.ui.newgame.NewGameScreen
import de.morzo.realmscore.ui.reveal.RevealScreen
import de.morzo.realmscore.ui.reveal.RoundSummaryScreen
import de.morzo.realmscore.ui.sandbox.SandboxLaunchData
import de.morzo.realmscore.ui.sandbox.SandboxScreen
import de.morzo.realmscore.ui.sandbox.favorites.SandboxFavoritesScreen
import de.morzo.realmscore.ui.sandbox.favorites.SandboxFavoritesViewModel
import de.morzo.realmscore.ui.summary.GameSummaryScreen
import de.morzo.realmscore.ui.tabs.history.HistoryScreen
import de.morzo.realmscore.ui.tabs.home.HomeScreen
import de.morzo.realmscore.ui.tabs.home.HomeViewModel
import de.morzo.realmscore.ui.tabs.settings.ProfileManagementScreen
import de.morzo.realmscore.ui.tabs.settings.ProfileManagementViewModel
import de.morzo.realmscore.ui.tabs.settings.SettingsScreen
import de.morzo.realmscore.ui.tabs.settings.SettingsViewModel
import de.morzo.realmscore.ui.tabs.settings.UsernameChangeScreen
import de.morzo.realmscore.ui.tabs.settings.UsernameChangeViewModel
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import de.morzo.realmscore.ui.tabs.stats.CardStatsOverviewScreen
import de.morzo.realmscore.ui.tabs.stats.CardStatsScreen
import de.morzo.realmscore.ui.tabs.stats.HeadToHeadScreen
import de.morzo.realmscore.ui.tabs.stats.PlayerStatsScreen
import de.morzo.realmscore.ui.tabs.stats.StatsOverviewScreen

private data class TabItem(
    val route: String,
    val navigateTo: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(Routes.TAB_HOME, Routes.TAB_HOME, R.string.tab_home, Icons.Default.Home),
    TabItem(Routes.TAB_HISTORY, Routes.TAB_HISTORY, R.string.tab_history, Icons.Default.DateRange),
    TabItem(Routes.TAB_STATS, Routes.TAB_STATS, R.string.tab_stats, Icons.AutoMirrored.Filled.List),
    TabItem(Routes.SANDBOX, Routes.sandboxRouteEmpty(), R.string.tab_sandbox, Icons.Default.Casino),
    TabItem(Routes.TAB_SETTINGS, Routes.TAB_SETTINGS, R.string.tab_settings, Icons.Default.Settings),
)

@Composable
fun MainScaffold(container: AppContainer) {
    val tabNavController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomNavigation(tabNavController) },
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = Routes.TAB_HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TAB_HOME) {
                val vm: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        profileRepo = container.profileRepository,
                        gameRepo = container.gameRepository,
                        roundRepo = container.roundRepository,
                        pickRandomStatUseCase = container.pickRandomStatUseCase,
                    ),
                )
                HomeScreen(navController = tabNavController, viewModel = vm)
            }
            composable(Routes.NEW_GAME) {
                NewGameScreen(
                    container = container,
                    onGameStarted = { gameId ->
                        tabNavController.navigate(Routes.gameRoute(gameId)) {
                            popUpTo(Routes.TAB_HOME)
                        }
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.GAME_IN_PROGRESS,
                arguments = listOf(navArgument(Routes.ARG_GAME_ID) { type = NavType.StringType }),
            ) { entry ->
                val gameId = entry.arguments?.getString(Routes.ARG_GAME_ID).orEmpty()
                GameInProgressScreen(
                    container = container,
                    gameId = gameId,
                    onStartRound = { roundId ->
                        tabNavController.navigate(Routes.roundCaptureRoute(roundId))
                    },
                    onMoveToSandbox = { gId, rid, pid ->
                        tabNavController.navigate(Routes.sandboxRouteFromRound(gId, rid, pid))
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.ROUND_ENTRY,
                arguments = listOf(navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }),
            ) { entry ->
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                RoundEntryScreen(
                    container = container,
                    roundId = roundId,
                    onEnterPlayer = { profileId ->
                        tabNavController.navigate(
                            Routes.playerHandEntryRoute(roundId, profileId)
                        )
                    },
                    onReveal = {
                        tabNavController.navigate(Routes.revealRoute(roundId))
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.ROUND_CAPTURE,
                arguments = listOf(navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }),
            ) { entry ->
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                RoundCaptureScreen(
                    container = container,
                    roundId = roundId,
                    onAllPlayersCaptured = {
                        tabNavController.navigate(Routes.revealRoute(roundId)) {
                            popUpTo(Routes.ROUND_CAPTURE) { inclusive = true }
                        }
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.PLAYER_HAND_ENTRY,
                arguments = listOf(
                    navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType },
                    navArgument(Routes.ARG_PROFILE_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                val profileId = entry.arguments?.getString(Routes.ARG_PROFILE_ID).orEmpty()
                PlayerHandEntryScreen(
                    container = container,
                    roundId = roundId,
                    profileId = profileId,
                    onSubmitDone = { tabNavController.popBackStack() },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.REVEAL,
                arguments = listOf(navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }),
            ) { entry ->
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                RevealScreen(
                    container = container,
                    roundId = roundId,
                    onDone = {
                        tabNavController.navigate(Routes.roundSummaryRoute(roundId)) {
                            popUpTo(Routes.REVEAL) { inclusive = true }
                        }
                    },
                    onSkip = {
                        tabNavController.navigate(Routes.roundSummaryRoute(roundId)) {
                            popUpTo(Routes.REVEAL) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.ROUND_SUMMARY,
                arguments = listOf(navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }),
            ) { entry ->
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                RoundSummaryScreen(
                    container = container,
                    roundId = roundId,
                    onNextRound = { newRoundId ->
                        tabNavController.navigate(Routes.roundCaptureRoute(newRoundId)) {
                            popUpTo(Routes.ROUND_SUMMARY) { inclusive = true }
                        }
                    },
                    onCompleteGame = { gameId ->
                        tabNavController.navigate(Routes.gameSummaryRoute(gameId))
                    },
                    onEditRound = {
                        tabNavController.navigate(Routes.roundCaptureRoute(roundId)) {
                            popUpTo(Routes.ROUND_SUMMARY) { inclusive = true }
                        }
                    },
                    onShowRevealAgain = {
                        tabNavController.navigate(Routes.revealRoute(roundId))
                    },
                    onMoveToSandbox = { gameId, rid, profileId ->
                        tabNavController.navigate(
                            Routes.sandboxRouteFromRound(gameId, rid, profileId),
                        )
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.GAME_SUMMARY,
                arguments = listOf(navArgument(Routes.ARG_GAME_ID) { type = NavType.StringType }),
            ) { entry ->
                val gameId = entry.arguments?.getString(Routes.ARG_GAME_ID).orEmpty()
                GameSummaryScreen(
                    container = container,
                    gameId = gameId,
                    onCloseGameDone = {
                        tabNavController.navigate(Routes.TAB_HOME) {
                            popUpTo(tabNavController.graph.findStartDestination().id) {
                                inclusive = false
                                saveState = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onShowStats = {
                        tabNavController.navigate(Routes.TAB_STATS) {
                            popUpTo(tabNavController.graph.findStartDestination().id) {
                                inclusive = false
                                saveState = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onBackToGame = {
                        tabNavController.navigate(Routes.gameRoute(gameId)) {
                            popUpTo(Routes.GAME_SUMMARY) { inclusive = true }
                        }
                    },
                    onMoveToSandbox = { gId, rid, pid ->
                        tabNavController.navigate(Routes.sandboxRouteFromRound(gId, rid, pid))
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(
                route = Routes.SANDBOX,
                arguments = listOf(
                    navArgument(Routes.ARG_LAUNCH_TYPE) {
                        type = NavType.StringType
                        defaultValue = Routes.SANDBOX_LAUNCH_EMPTY
                    },
                    navArgument(Routes.ARG_GAME_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_ROUND_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_PROFILE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_FAVORITE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val launchType = entry.arguments?.getString(Routes.ARG_LAUNCH_TYPE).orEmpty()
                val gameId = entry.arguments?.getString(Routes.ARG_GAME_ID).orEmpty()
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID).orEmpty()
                val profileId = entry.arguments?.getString(Routes.ARG_PROFILE_ID).orEmpty()
                val favoriteId = entry.arguments?.getString(Routes.ARG_FAVORITE_ID).orEmpty()
                val launchData: SandboxLaunchData = when {
                    launchType == Routes.SANDBOX_LAUNCH_FROM_ROUND &&
                        gameId.isNotEmpty() && roundId.isNotEmpty() && profileId.isNotEmpty() ->
                        SandboxLaunchData.FromRound(
                            gameId = gameId,
                            roundId = roundId,
                            profileId = profileId,
                        )

                    launchType == Routes.SANDBOX_LAUNCH_FROM_FAVORITE && favoriteId.isNotEmpty() ->
                        SandboxLaunchData.FromFavorite(favoriteId = favoriteId)

                    else -> SandboxLaunchData.Empty
                }
                SandboxScreen(
                    container = container,
                    launchData = launchData,
                    onOpenFavorites = {
                        tabNavController.navigate(Routes.sandboxFavoritesRoute())
                    },
                )
            }
            composable(Routes.SANDBOX_FAVORITES) {
                val vm: SandboxFavoritesViewModel = viewModel(
                    factory = SandboxFavoritesViewModel.Factory(
                        repo = container.sandboxFavoriteRepository,
                    ),
                )
                SandboxFavoritesScreen(
                    viewModel = vm,
                    onLoad = { favorite ->
                        tabNavController.navigate(Routes.sandboxRouteFromFavorite(favorite.id)) {
                            popUpTo(Routes.SANDBOX_FAVORITES) { inclusive = true }
                        }
                    },
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(Routes.TAB_HISTORY) {
                HistoryScreen(
                    container = container,
                    onOpenGame = { gameId, isClosed ->
                        val target = if (isClosed) {
                            Routes.gameSummaryRoute(gameId)
                        } else {
                            Routes.gameRoute(gameId)
                        }
                        tabNavController.navigate(target)
                    },
                )
            }
            composable(Routes.TAB_STATS) {
                StatsOverviewScreen(
                    container = container,
                    onOpenPlayer = { profileId ->
                        tabNavController.navigate(Routes.playerStatsRoute(profileId))
                    },
                    onOpenCard = { cardKey ->
                        tabNavController.navigate(Routes.cardStatsRoute(cardKey))
                    },
                    onOpenCardOverview = {
                        tabNavController.navigate(Routes.cardStatsOverviewRoute())
                    },
                )
            }
            composable(
                route = Routes.PLAYER_STATS,
                arguments = listOf(navArgument(Routes.ARG_PROFILE_ID) { type = NavType.StringType }),
            ) { entry ->
                val profileId = entry.arguments?.getString(Routes.ARG_PROFILE_ID).orEmpty()
                PlayerStatsScreen(
                    container = container,
                    profileId = profileId,
                    onBack = { tabNavController.popBackStack() },
                    onOpenOpponent = { opponentId ->
                        tabNavController.navigate(
                            Routes.headToHeadRoute(profileId, opponentId)
                        )
                    },
                    onMoveToSandbox = { gId, rid, pid ->
                        tabNavController.navigate(Routes.sandboxRouteFromRound(gId, rid, pid))
                    },
                )
            }
            composable(Routes.CARD_STATS_OVERVIEW) {
                CardStatsOverviewScreen(
                    container = container,
                    onBack = { tabNavController.popBackStack() },
                    onOpenCard = { cardKey ->
                        tabNavController.navigate(Routes.cardStatsRoute(cardKey))
                    },
                )
            }
            composable(
                route = Routes.CARD_STATS,
                arguments = listOf(navArgument(Routes.ARG_CARD_KEY) { type = NavType.StringType }),
            ) { entry ->
                val cardKey = entry.arguments?.getString(Routes.ARG_CARD_KEY).orEmpty()
                CardStatsScreen(
                    container = container,
                    cardKey = cardKey,
                    onBack = { tabNavController.popBackStack() },
                    onMoveToSandbox = { gId, rid, pid ->
                        tabNavController.navigate(Routes.sandboxRouteFromRound(gId, rid, pid))
                    },
                )
            }
            composable(
                route = Routes.HEAD_TO_HEAD,
                arguments = listOf(
                    navArgument(Routes.ARG_PROFILE_ID_A) { type = NavType.StringType },
                    navArgument(Routes.ARG_PROFILE_ID_B) { type = NavType.StringType },
                ),
            ) { entry ->
                val a = entry.arguments?.getString(Routes.ARG_PROFILE_ID_A).orEmpty()
                val b = entry.arguments?.getString(Routes.ARG_PROFILE_ID_B).orEmpty()
                HeadToHeadScreen(
                    container = container,
                    profileIdA = a,
                    profileIdB = b,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(Routes.TAB_SETTINGS) {
                val activity = LocalContext.current as? Activity
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        settings = container.settingsRepository,
                        profileRepo = container.profileRepository,
                        resetUseCase = container.resetUseCase,
                        backupRepo = container.backupRepository,
                        db = container.database,
                    ),
                )
                SettingsScreen(
                    viewModel = vm,
                    onChangeUsername = {
                        tabNavController.navigate(Routes.USERNAME_CHANGE)
                    },
                    onManageProfiles = {
                        tabNavController.navigate(Routes.PROFILE_MANAGEMENT)
                    },
                    onAppReset = { activity?.recreate() },
                )
            }
            composable(Routes.PROFILE_MANAGEMENT) {
                val vm: ProfileManagementViewModel = viewModel(
                    factory = ProfileManagementViewModel.Factory(
                        repo = container.profileRepository,
                    ),
                )
                ProfileManagementScreen(
                    viewModel = vm,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable(Routes.USERNAME_CHANGE) {
                val vm: UsernameChangeViewModel = viewModel(
                    factory = UsernameChangeViewModel.Factory(
                        profileRepo = container.profileRepository,
                    ),
                )
                UsernameChangeScreen(
                    viewModel = vm,
                    onDone = { tabNavController.popBackStack() },
                    onBack = { tabNavController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.navigateTo) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
