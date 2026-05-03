package com.feelvision.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.feelvision.ui.screens.debug.DebugPanelScreen
import com.feelvision.ui.screens.main.MainScreen
import com.feelvision.ui.screens.people.EnrollScreen
import com.feelvision.ui.screens.people.PeopleScreen
import com.feelvision.ui.screens.settings.SettingsScreen
import com.feelvision.ui.screens.splash.SplashScreen
import com.feelvision.hardware.PhoneCameraSource

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object People : Screen("people")
    data object Enroll : Screen("enroll/{personId}") {
        fun route(id: Long = -1L) = "enroll/$id"
    }
    data object Debug : Screen("debug")
}

@Composable
fun NavGraph(navController: NavHostController, showDebug: Boolean, cameraSource: PhoneCameraSource) {
    NavHost(navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen {
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            }
        }

        composable(Screen.Main.route) {
            MainScreen(
                cameraSource = cameraSource,
                onSettings = { navController.navigate(Screen.Settings.route) },
                onPeople = { navController.navigate(Screen.People.route) },
                onDebug = { if (showDebug) navController.navigate(Screen.Debug.route) },
                showDebugButton = showDebug
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.People.route) {
            PeopleScreen(
                onEnroll = { id -> navController.navigate(Screen.Enroll.route(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Enroll.route,
            arguments = listOf(navArgument("personId") { type = NavType.LongType })
        ) {
            EnrollScreen(
                cameraSource = cameraSource,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Debug.route) {
            DebugPanelScreen(
                cameraSource = cameraSource,
                onBack = { navController.popBackStack() }
            )
        }
    }
}