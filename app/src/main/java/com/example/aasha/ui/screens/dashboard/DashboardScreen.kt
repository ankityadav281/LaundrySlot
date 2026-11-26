package com.example.aasha.ui.screens.dashboard

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.aasha.ui.components.ActiveWashProgressCard
import com.example.aasha.ui.components.DashboardLoadingState
import com.example.aasha.ui.components.EmptyMachineSearchState
import com.example.aasha.ui.components.LaundryCard
import com.example.aasha.ui.components.MachineCard
import com.example.aasha.ui.components.MachineDetailsBottomSheet
import com.example.aasha.ui.components.MachineFilterSection
import com.example.aasha.ui.components.MachineSummaryRow
import com.example.aasha.ui.components.NetworkErrorStateView
import com.example.aasha.ui.components.NoMachinesAvailableState
import com.example.aasha.ui.components.OfflineStatusBanner
import com.example.aasha.ui.components.QuickStartBottomSheet
import com.example.aasha.ui.navigation.Screen
import com.example.aasha.viewmodel.DashboardEvent
import com.example.aasha.viewmodel.DashboardUiState
import com.example.aasha.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DashboardEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DashboardHeader(
                uiState = uiState,
                onNotificationsClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleNotifications()
                },
                onRefreshClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.refreshMachines()
                },
                onSearchQueryChanged = viewModel::updateSearchQuery
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Offline status banner
                item(key = "offline_banner") {
                    AnimatedVisibility(
                        visible = !uiState.isOnline,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OfflineStatusBanner(
                            lastSyncedLabel = formatLastSyncTime(uiState.lastSyncTime)
                        )
                    }
                }

                // Network/sync error banner with Retry & last sync time
                item(key = "error_banner") {
                    AnimatedVisibility(
                        visible = uiState.errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        uiState.errorMessage?.let { error ->
                            NetworkErrorStateView(
                                message = error,
                                lastSyncedLabel = formatLastSyncTime(uiState.lastSyncTime),
                                onRetry = { viewModel.refreshMachines() }
                            )
                        }
                    }
                }

                // Active wash progress card when hosteller has a running cycle
                uiState.activeBooking?.let { active ->
                    item(key = "active_wash_${active.id}") {
                        ActiveWashProgressCard(
                            booking = active,
                            onCompleteClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.completeActiveCycle(active.machineId, active.id)
                            },
                            onViewTrackerClick = {
                                navController.navigate(Screen.MySlots.route) {
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

                if (uiState.isLoading) {
                    item(key = "loading_skeleton") {
                        DashboardLoadingState()
                    }
                } else {
                    // Machine Summary Cards (Available, In Use, Maintenance)
                    item(key = "summary_cards") {
                        MachineSummaryRow(
                            availableCount = uiState.availableCount,
                            inUseCount = uiState.inUseCount,
                            maintenanceCount = uiState.outOfOrderCount,
                            selectedStatus = uiState.selectedStatusFilter,
                            onSummaryClick = { status -> viewModel.toggleStatusFilter(status) }
                        )
                    }

                    // Dynamic Interactive Filter Chips
                    item(key = "filter_chips") {
                        MachineFilterSection(
                            availableBlocks = uiState.availableBlocks,
                            availableFloors = uiState.availableFloors,
                            selectedBlock = uiState.selectedBlock,
                            selectedFloor = uiState.selectedFloor,
                            selectedTypeFilter = uiState.selectedTypeFilter,
                            showAvailableOnly = uiState.showAvailableOnly,
                            hasActiveFilters = uiState.hasActiveFilters,
                            onBlockSelected = viewModel::onBlockSelected,
                            onFloorSelected = viewModel::onFloorSelected,
                            onTypeSelected = viewModel::onTypeFilterSelected,
                            onToggleAvailableOnly = viewModel::toggleAvailableOnly,
                            onResetFilters = viewModel::clearFilters
                        )
                    }

                    // Machines Section Header
                    item(key = "section_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hostel Machines",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            AnimatedContent(
                                targetState = uiState.machineItems.size,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "machine_count_anim"
                            ) { count ->
                                Text(
                                    text = "$count of ${uiState.totalCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Empty / No Search Results / Machine List
                    when {
                        uiState.totalCount == 0 -> {
                            item(key = "empty_machines") {
                                NoMachinesAvailableState(
                                    onRefresh = { viewModel.refreshMachines() }
                                )
                            }
                        }

                        uiState.machineItems.isEmpty() -> {
                            item(key = "empty_search") {
                                EmptyMachineSearchState(
                                    onClearFilters = { viewModel.clearFilters() }
                                )
                            }
                        }

                        else -> {
                            items(
                                items = uiState.machineItems,
                                key = { it.machine.id }
                            ) { item ->
                                MachineCard(
                                    model = item,
                                    isOnline = uiState.isOnline,
                                    onCardClick = {
                                        viewModel.selectMachine(item.machine.id)
                                    },
                                    onReserveSlotClick = {
                                        navController.navigate(Screen.BookSlot.createRoute(item.machine.id)) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    onStartNowClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.openQuickStartSheet(item.machine.id)
                                    },
                                    onViewDetailsClick = {
                                        viewModel.selectMachine(item.machine.id, expandFaultSection = false)
                                    },
                                    onReportFaultClick = {
                                        viewModel.selectMachine(item.machine.id, expandFaultSection = true)
                                    }
                                )
                            }
                        }
                    }

                    // Hostel Etiquette Notice
                    item(key = "guidelines_card") {
                        LaundryCard(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Hostel Laundry Etiquette",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Collect clothes within 10 minutes of cycle completion. Peak hours: 6:00 PM – 9:30 PM.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // Machine Details & Fault Reporting ModalBottomSheet
    uiState.selectedMachineForDetails?.let { selectedModel ->
        MachineDetailsBottomSheet(
            machineModel = selectedModel,
            isOnline = uiState.isOnline,
            lastSyncTime = uiState.lastSyncTime,
            initiallyExpandFault = uiState.expandFaultInDetails,
            isSubmittingFault = uiState.isSubmittingFault,
            faultSubmitError = uiState.faultSubmitError,
            onDismiss = { viewModel.dismissMachineDetails() },
            onReserveSlot = {
                val id = selectedModel.machine.id
                viewModel.dismissMachineDetails()
                navController.navigate(Screen.BookSlot.createRoute(id)) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = false
                    }
                    launchSingleTop = true
                }
            },
            onQuickStart = {
                val id = selectedModel.machine.id
                viewModel.dismissMachineDetails()
                viewModel.openQuickStartSheet(id)
            },
            onSubmitFaultReport = { categories, description ->
                viewModel.reportMachineIssue(
                    machineId = selectedModel.machine.id,
                    selectedCategories = categories,
                    description = description
                )
            }
        )
    }

    // Quick Start ModalBottomSheet
    uiState.selectedMachineForQuickStart?.let { machine ->
        QuickStartBottomSheet(
            machine = machine,
            isOnline = uiState.isOnline,
            isStarting = uiState.isStartingWash,
            errorMessage = uiState.quickStartError,
            onDismiss = { viewModel.dismissQuickStartSheet() },
            onStartWashNow = { cycleType ->
                viewModel.startQuickWash(machine.id, cycleType)
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    uiState: DashboardUiState,
    onNotificationsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${uiState.greeting}, ${uiState.hostellerName}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${uiState.hostelBlock} • Room ${uiState.roomNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = if (uiState.notificationsEnabled) {
                                Icons.Outlined.NotificationsActive
                            } else {
                                Icons.Outlined.NotificationsOff
                            },
                            contentDescription = if (uiState.notificationsEnabled) {
                                "Notifications enabled"
                            } else {
                                "Notifications paused"
                            },
                            tint = if (uiState.notificationsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !uiState.isRefreshing,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh machine status",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = "Search by name, ID, block, or type…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = uiState.searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    }
}

private fun formatLastSyncTime(lastSyncMillis: Long): String {
    if (lastSyncMillis <= 0L) return "Not synced yet"
    return DateUtils.getRelativeTimeSpanString(
        lastSyncMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}
