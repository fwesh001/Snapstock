package com.example.snapstock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snapstock.ui.BatchCaptureScreen
import com.example.snapstock.ui.DashboardScreen
import com.example.snapstock.ui.Route
import com.example.snapstock.ui.SearchScreen
import com.example.snapstock.ui.SettingsScreen
import com.example.snapstock.ui.SplashScreenContent
import com.example.snapstock.ui.theme.SnapStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SnapStockTheme {
                val navController = rememberNavController()
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
                            onSearchClick = { navController.navigate(Route.Search.route) },
                            onSettingsClick = { navController.navigate(Route.Settings.route) },
                            onBatchCaptureClick = { navController.navigate(Route.BatchCapture.route) }
                        )
                    }
                    composable(Route.Search.route) {
                        SearchScreen(onBackClick = { navController.popBackStack() })
                    }
                    composable(Route.Settings.route) {
                        SettingsScreen(onBackClick = { navController.popBackStack() })
                    }
                    composable(Route.BatchCapture.route) {
                        BatchCaptureScreen(onBackClick = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
