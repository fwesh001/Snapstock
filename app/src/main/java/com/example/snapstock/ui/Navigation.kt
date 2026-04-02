package com.example.snapstock.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

sealed class Route(val route: String) {
    object Splash : Route("splash")
    object Dashboard : Route("dashboard")
    object Search : Route("search")
    object Settings : Route("settings")
    object BatchCapture : Route("batch_capture")
    object BatchEntry : Route("batch_entry")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val batchEntryViewModel: BatchEntryViewModel = viewModel()

    val navigateToDashboard = {
        navController.navigate(Route.Dashboard.route) {
            launchSingleTop = true
            popUpTo(Route.Dashboard.route) { inclusive = false }
        }
    }

    val navigateToSearch = {
        navController.navigate(Route.Search.route) {
            launchSingleTop = true
        }
    }

    val navigateToSettings = {
        navController.navigate(Route.Settings.route) {
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Route.Splash.route) {
        composable(Route.Splash.route) {
            SplashScreenContent(
                onAnimationEnd = {
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Route.Dashboard.route) {
            DashboardScreen(
                onSearchClick = navigateToSearch,
                onSettingsClick = navigateToSettings,
                onBatchCaptureClick = { navController.navigate(Route.BatchCapture.route) }
            )
        }
        composable(Route.Search.route) {
            SearchScreen(
                onHomeClick = navigateToDashboard,
                onCollectionClick = { },
                onSettingsClick = navigateToSettings
            )
        }
        composable(Route.Settings.route) {
            SettingsScreen(
                onHomeClick = navigateToDashboard,
                onCollectionClick = { },
                onSettingsClick = { }
            )
        }
        composable(Route.BatchCapture.route) {
            BatchCaptureScreen(
                onDoneClick = { navController.navigate(Route.BatchEntry.route) },
                batchEntryViewModel = batchEntryViewModel
            )
        }
        composable(Route.BatchEntry.route) {
            BatchEntryScreen(
                onBackClick = { navController.popBackStack() },
                onSaveComplete = {
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Dashboard.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                batchEntryViewModel = batchEntryViewModel
            )
        }
    }
}

