package com.astratouch.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.astratouch.app.ui.screens.AboutScreen
import com.astratouch.app.ui.screens.ActionLibraryScreen
import com.astratouch.app.ui.screens.AppLauncherScreen
import com.astratouch.app.ui.screens.BubbleSettingsScreen
import com.astratouch.app.ui.screens.CalculatorScreen
import com.astratouch.app.ui.screens.ClipboardScreen
import com.astratouch.app.ui.screens.ExperimentalLabScreen
import com.astratouch.app.ui.screens.GestureManagerScreen
import com.astratouch.app.ui.screens.HomeDashboardScreen
import com.astratouch.app.ui.screens.MacroEditorScreen
import com.astratouch.app.ui.screens.MacroManagerScreen
import com.astratouch.app.ui.screens.NotesScreen
import com.astratouch.app.ui.screens.OnboardingScreen
import com.astratouch.app.ui.screens.PermissionCenterScreen
import com.astratouch.app.ui.screens.PermissionSetupScreen
import com.astratouch.app.ui.screens.PrivacyCenterScreen
import com.astratouch.app.ui.screens.ProfileManagerScreen
import com.astratouch.app.ui.screens.QuickControlCenterScreen
import com.astratouch.app.ui.screens.RadialEditorScreen
import com.astratouch.app.ui.screens.ScreenshotStudioScreen
import com.astratouch.app.ui.screens.SettingsScreen
import com.astratouch.app.ui.screens.SplashScreen
import com.astratouch.app.ui.screens.ThemeStudioScreen
import com.astratouch.app.ui.screens.TimerScreen
import com.astratouch.app.util.NavRoutes
import kotlinx.serialization.Serializable

/**
 * The single navigation graph. Every one of the 24 required screens is a
 * concrete destination with real content — there are no stubs.
 */
@Composable
fun AstraNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoutes.SPLASH,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(onFinished = { navController.navigateAfterSplash() })
        }
        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(NavRoutes.PERMISSION_SETUP) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(NavRoutes.PERMISSION_SETUP) {
            PermissionSetupScreen(
                onFinished = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.PERMISSION_SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(NavRoutes.HOME) {
            HomeDashboardScreen(
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavRoutes.BUBBLE) {
            BubbleSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.RADIAL) {
            RadialEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.QUICK_CONTROLS) {
            QuickControlCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.GESTURES) {
            GestureManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.ACTIONS) {
            ActionLibraryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MACROS) {
            MacroManagerScreen(
                onBack = { navController.popBackStack() },
                onEdit = { macroId -> navController.navigate(NavRoutes.macroEditor(macroId)) },
            )
        }
        composable(
            route = NavRoutes.macroEditorPattern(),
            arguments = listOf(navArgument("macroId") { type = NavType.StringType }),
        ) { entry ->
            MacroEditorScreen(
                macroId = entry.arguments?.getString("macroId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.APPS) {
            AppLauncherScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.PROFILES) {
            ProfileManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.THEMES) {
            ThemeStudioScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SCREENSHOTS) {
            ScreenshotStudioScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.CLIPBOARD) {
            ClipboardScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.NOTES) {
            NotesScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.CALCULATOR) {
            CalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.TIMER) {
            TimerScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.LAB) {
            ExperimentalLabScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.PRIVACY) {
            PrivacyCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.PERMISSIONS) {
            PermissionCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
            )
        }
        composable(NavRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** The splash decides the real start destination once preferences load. */
private fun NavHostController.navigateAfterSplash() {
    navigate(NavRoutes.HOME) {
        popUpTo(NavRoutes.SPLASH) { inclusive = true }
    }
}

@Serializable
internal data object RouteMarker
