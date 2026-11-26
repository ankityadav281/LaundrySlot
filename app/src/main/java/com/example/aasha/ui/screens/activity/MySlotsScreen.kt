package com.example.aasha.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aasha.R
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.ui.components.ActiveWashProgressCard
import com.example.aasha.ui.components.EmptyStateView
import com.example.aasha.ui.components.LaundrySlotCard
import com.example.aasha.viewmodel.MySlotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySlotsScreen(
    viewModel: MySlotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshTime()
        onPauseOrDispose {}
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active & Upcoming, 1 = History
    var showCancelDialogForBooking by remember { mutableStateOf<SlotBooking?>(null) }

    val activeAndUpcomingCount = uiState.activeBookings.size + uiState.upcomingBookings.size

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.my_laundry_activity),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Track active cycles, upcoming slots, and past washes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    text = "${stringResource(R.string.tab_active_upcoming)} ($activeAndUpcomingCount)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    text = "${stringResource(R.string.tab_history)} (${uiState.completedBookings.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    if (uiState.activeBookings.isNotEmpty()) {
                        item(key = "active_header") {
                            Text(
                                text = "Running Cycles",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(uiState.activeBookings, key = { it.id }) { active ->
                            ActiveWashProgressCard(
                                booking = active,
                                onCompleteClick = {
                                    viewModel.completeCycle(active.machineId, active.id)
                                },
                                onViewTrackerClick = {}
                            )
                        }
                    }

                    if (uiState.upcomingBookings.isNotEmpty()) {
                        item(key = "upcoming_header") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scheduled Slots",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(uiState.upcomingBookings, key = { it.id }) { booking ->
                            LaundrySlotCard(
                                booking = booking,
                                currentTimeMillis = uiState.currentTimeMillis,
                                onCancelClick = { showCancelDialogForBooking = booking }
                            )
                        }
                    }

                    if (uiState.activeBookings.isEmpty() && uiState.upcomingBookings.isEmpty()) {
                        item(key = "empty_active") {
                            EmptyStateView(
                                icon = Icons.Outlined.LocalLaundryService,
                                title = stringResource(R.string.no_active_slots),
                                subtitle = "Start a quick wash from Machines or reserve a slot in Book Slot."
                            )
                        }
                    }
                } else {
                    if (uiState.completedBookings.isEmpty()) {
                        item(key = "empty_history") {
                            EmptyStateView(
                                icon = Icons.Outlined.EventBusy,
                                title = stringResource(R.string.no_history_slots),
                                subtitle = "Completed, expired, or cancelled laundry slots will appear here."
                            )
                        }
                    } else {
                        items(uiState.completedBookings, key = { it.id }) { booking ->
                            LaundrySlotCard(
                                booking = booking,
                                currentTimeMillis = uiState.currentTimeMillis
                            )
                        }
                    }
                }
            }
        }
    }

    // Cancel Slot Dialog
    showCancelDialogForBooking?.let { booking ->
        AlertDialog(
            onDismissRequest = { showCancelDialogForBooking = null },
            title = {
                Text(
                    text = stringResource(R.string.cancel_booking_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to cancel your slot for ${booking.machineName} (${booking.timeSlot})?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelBooking(booking.id)
                        showCancelDialogForBooking = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Cancel Slot")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialogForBooking = null }) {
                    Text("Keep Slot")
                }
            }
        )
    }
}
