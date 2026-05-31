package de.morzo.realmscore.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.onboarding.OnboardingScreen
import de.morzo.realmscore.ui.onboarding.OnboardingViewModel

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        composable(Routes.SPLASH) {
            SplashRoute(
                container = container,
                onOwnerExists = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNoOwner = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(container.profileRepository)
            )
            OnboardingScreen(
                viewModel = vm,
                onOwnerCreated = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainScaffold(container = container)
        }
    }
}

@Composable
private fun SplashRoute(
    container: AppContainer,
    onOwnerExists: () -> Unit,
    onNoOwner: () -> Unit,
) {
    LaunchedEffect(Unit) {
        val owner = container.profileRepository.getLocalOwner()
        if (owner != null) onOwnerExists() else onNoOwner()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
