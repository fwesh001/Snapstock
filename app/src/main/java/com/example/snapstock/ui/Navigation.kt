package com.example.snapstock.ui

sealed class Route(val route: String) {
    object Splash : Route("splash")
    object Dashboard : Route("dashboard")
    object Search : Route("search")
    object Settings : Route("settings")
    object BatchCapture : Route("batch_capture")
}
