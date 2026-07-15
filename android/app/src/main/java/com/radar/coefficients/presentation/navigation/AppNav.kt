package com.radar.coefficients.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.radar.coefficients.presentation.city.CityPickerScreen
import com.radar.coefficients.presentation.map.MapScreen
import com.radar.coefficients.presentation.radar.RadarScreen
import com.radar.coefficients.presentation.settings.SettingsScreen
import com.radar.coefficients.presentation.sources.SourcesScreen

object Routes {
    const val MAP = "map"
    const val RADAR = "radar"
    const val SETTINGS = "settings"
    const val SOURCES = "sources"
    const val CITY = "city"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val tabs = listOf(
        Triple(Routes.MAP, "Карта", Icons.Default.Map),
        Triple(Routes.RADAR, "Радар", Icons.Default.Radar),
        Triple(Routes.SOURCES, "Источники", Icons.Default.Info),
        Triple(Routes.SETTINGS, "Настройки", Icons.Default.Settings)
    )

    Scaffold(
        bottomBar = {
            if (current != Routes.CITY) {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = current == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.MAP,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.MAP) {
                MapScreen(
                    onOpenCityPicker = { navController.navigate(Routes.CITY) }
                )
            }
            composable(Routes.RADAR) {
                RadarScreen(onOpenCityPicker = { navController.navigate(Routes.CITY) })
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.SOURCES) { SourcesScreen() }
            composable(Routes.CITY) {
                CityPickerScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
