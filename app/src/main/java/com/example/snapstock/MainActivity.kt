package com.example.snapstock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.snapstock.ui.AppNavHost
import com.example.snapstock.ui.theme.SnapStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            SnapStockTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
