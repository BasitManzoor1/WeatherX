package com.devorbit.weatherx

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val icon: ImageVector) {
    data object Main : Screen("Home", Icons.Rounded.Home)
    data object Forecast : Screen("Forecast", Icons.AutoMirrored.Rounded.List)
    data object Emergency : Screen("Emergency", Icons.Rounded.HealthAndSafety)
    data object Settings : Screen("Settings", Icons.Rounded.Settings)
}