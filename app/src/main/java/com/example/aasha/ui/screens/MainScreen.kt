package com.example.aasha.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aasha.ui.navigation.BottomNavItem
import com.example.aasha.ui.navigation.Screen
import com.example.aasha.ui.screens.activity.MySlotsScreen
import com.example.aasha.ui.screens.booking.BookingScreen
import com.example.aasha.ui.screens.dashboard.DashboardScreen
import com.example.aasha.ui.screens.issues.ReportIssueScreen
import com.example.aasha.ui.screens.login.LoginScreen
import com.example.aasha.ui.screens.login.ScreenMode
import com.example.aasha.ui.screens.profile.ProfileScreen
import com.example.aasha.ui.screens.splash.SplashScreen
import com.example.aasha.viewmodel.SessionViewModel

@Composable
fun MainScreen(
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isLoggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn && navController.currentDestination?.route != Screen.Splash.route) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val bottomBarScreens = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.BookSlot,
        BottomNavItem.MySlots,
        BottomNavItem.Profile
    )

    val showBottomBar = currentRoute != null && (
        currentRoute == Screen.Dashboard.route ||
            currentRoute.startsWith(Screen.BookSlot.route) ||
            currentRoute == Screen.MySlots.route ||
            currentRoute == Screen.Profile.route
        )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    bottomBarScreens.forEach { item ->
                        val selected = currentDestination.hierarchy.any {
                            it.route?.startsWith(item.route) == true
                        }
                        val title = stringResource(item.titleResId)

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = title) },
                            label = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMpin = {
                        navController.navigate(Screen.LoginMpin.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    showMpinInitially = false
                )
            }
            composable(Screen.LoginMpin.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.LoginMpin.route) { inclusive = true }
                        }
                    },
                    showMpinInitially = true
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(navController = navController)
            }
            composable(
                route = "${Screen.BookSlot.route}?machineId={machineId}",
                arguments = listOf(
                    navArgument("machineId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                BookingScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MySlots.route) {
                MySlotsScreen()
            }
            composable(Screen.ReportIssue.route) {
                ReportIssueScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Profile.route) {
                ProfileScreen(navController = navController)
            }
            composable(Screen.SetupMpin.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.popBackStack()
                    },
                    showMpinInitially = false,
                    initialMode = ScreenMode.SetupMpin
                )
            }
        }
    }
}
