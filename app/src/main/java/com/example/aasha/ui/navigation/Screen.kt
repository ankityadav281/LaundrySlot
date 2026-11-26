package com.example.aasha.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object LoginMpin : Screen("login_mpin")
    object SetupMpin : Screen("setup_mpin")
    object Dashboard : Screen("dashboard")
    object BookSlot : Screen("book_slot") {
        fun createRoute(machineId: String? = null): String =
            if (machineId.isNullOrBlank()) route else "$route?machineId=$machineId"
    }
    object MySlots : Screen("my_slots")
    object ReportIssue : Screen("report_issue")
    object Profile : Screen("profile")
}
