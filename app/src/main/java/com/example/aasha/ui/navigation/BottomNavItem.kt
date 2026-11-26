package com.example.aasha.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.aasha.R

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    object Dashboard : BottomNavItem(Screen.Dashboard.route, R.string.nav_machines, Icons.Default.LocalLaundryService)
    object BookSlot : BottomNavItem(Screen.BookSlot.route, R.string.nav_book_slot, Icons.Default.CalendarMonth)
    object MySlots : BottomNavItem(Screen.MySlots.route, R.string.nav_my_slots, Icons.Default.Timer)
    object Profile : BottomNavItem(Screen.Profile.route, R.string.nav_profile, Icons.Default.Person)
}
