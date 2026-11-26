package com.example.aasha.ui.screens.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aasha.R
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.SlotBooking
import com.example.aasha.domain.model.SlotTimeHelper
import com.example.aasha.domain.model.SyncStatus
import com.example.aasha.domain.model.categoryLabel
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.ui.components.LaundryButton
import com.example.aasha.ui.components.LaundryCard
import com.example.aasha.ui.components.LaundryCardVariant
import com.example.aasha.ui.components.LaundryTextField
import com.example.aasha.ui.components.MachineStatusBadge
import com.example.aasha.ui.components.rememberStatusColors
import com.example.aasha.viewmodel.BookingResult
import com.example.aasha.viewmodel.BookingUiState
import com.example.aasha.viewmodel.BookingViewModel
import com.example.aasha.viewmodel.SlotAvailability
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BookingScreen(
    onBack: () -> Unit = {},
    viewModel: BookingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeSlots = viewModel.availableTimeSlots

    var showSuccessDialog by remember { mutableStateOf<SlotBooking?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCurrentTime()
        onPauseOrDispose {}
    }

    LaunchedEffect(uiState.result) {
        when (val result = uiState.result) {
            is BookingResult.Success -> {
                showSuccessDialog = result.booking
                viewModel.resetResult()
            }
            is BookingResult.Error -> {
                errorMessage = result.message
                viewModel.resetResult()
            }
            else -> {}
        }
    }

    val dates = remember(SlotTimeHelper.getStartOfDay(uiState.currentTimeMillis)) {
        val list = mutableListOf<Triple<String, String, Long>>()
        val cal = Calendar.getInstance()
        val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayNumFormat = SimpleDateFormat("dd", Locale.getDefault())
        for (i in 0..4) {
            val topLabel = when (i) {
                0 -> "Today"
                1 -> "Tmrw"
                else -> dayNameFormat.format(cal.time)
            }
            list.add(Triple(topLabel, dayNumFormat.format(cal.time), cal.timeInMillis))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val selectedDateFormatted = remember(uiState.selectedDate) {
        SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(uiState.selectedDate))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.book_laundry_slot),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select date, machine, time slot, and cycle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (!uiState.isOnline) {
                    item(key = "offline_booking_notice") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Offline mode: bookings are saved locally as pending and confirmed when cloud sync completes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Step 1: Date Selector
                item(key = "step_date") {
                    StepHeader(stepNumber = "1", title = "Select Date")
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dates, key = { it.third }) { (dayLabel, dateNum, timestamp) ->
                            val startOfDay = BookingUiState.getStartOfDay(timestamp)
                            val isSelected = uiState.selectedDate == startOfDay

                            Surface(
                                onClick = { viewModel.selectDate(timestamp) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = if (isSelected) null else BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(68.dp)
                                    .defaultMinSize(minHeight = 64.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = dateNum,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Step 2: Machine Selector
                item(key = "step_machine") {
                    StepHeader(stepNumber = "2", title = "Select Machine")
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.allMachines, key = { it.id }) { machine ->
                            val isSelected = uiState.selectedMachine?.id == machine.id
                            val isOutOfOrder = machine.status == MachineStatus.OUT_OF_ORDER

                            LaundryCard(
                                variant = LaundryCardVariant.Interactive,
                                onClick = if (!isOutOfOrder) {
                                    { viewModel.selectMachine(machine) }
                                } else null,
                                containerColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    isOutOfOrder -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier.width(176.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocalLaundryService,
                                        contentDescription = null,
                                        tint = if (isOutOfOrder) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = machine.id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = machine.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${machine.blockName} • Fl ${machine.floor} • ${machine.capacityKg} kg",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                MachineStatusBadge(
                                    status = machine.status,
                                    animateIndicator = false
                                )
                            }
                        }
                    }
                }

                // Step 3: Time Slot Grid
                item(key = "step_slot") {
                    StepHeader(stepNumber = "3", title = "Select 1-Hour Time Slot")
                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.selectedMachine != null && uiState.selectedTimeSlot.isBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "All slots for today have started or been booked. Choose Tomorrow or a later date.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        timeSlots.chunked(2).forEach { rowSlots ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSlots.forEach { slot ->
                                    val slotState = uiState.selectedMachine?.let {
                                        viewModel.getSlotAvailability(it.id, slot, uiState)
                                    } ?: SlotAvailability.PAST

                                    val isEnabled = slotState == SlotAvailability.AVAILABLE ||
                                        slotState == SlotAvailability.SELECTED
                                    val isSelected = slotState == SlotAvailability.SELECTED

                                    val containerColor = when (slotState) {
                                        SlotAvailability.SELECTED -> MaterialTheme.colorScheme.primary
                                        SlotAvailability.BOOKED_BY_YOU -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        SlotAvailability.OCCUPIED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                        SlotAvailability.PAST -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        SlotAvailability.AVAILABLE -> MaterialTheme.colorScheme.surface
                                    }

                                    val borderColor = when (slotState) {
                                        SlotAvailability.BOOKED_BY_YOU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        SlotAvailability.OCCUPIED -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                                        SlotAvailability.PAST -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }

                                    val textColor = when (slotState) {
                                        SlotAvailability.SELECTED -> MaterialTheme.colorScheme.onPrimary
                                        SlotAvailability.BOOKED_BY_YOU -> MaterialTheme.colorScheme.primary
                                        SlotAvailability.OCCUPIED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        SlotAvailability.PAST -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                                        SlotAvailability.AVAILABLE -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Surface(
                                        onClick = { if (isEnabled) viewModel.selectTimeSlot(slot) },
                                        enabled = isEnabled,
                                        shape = RoundedCornerShape(12.dp),
                                        color = containerColor,
                                        border = if (isSelected) null else BorderStroke(1.dp, borderColor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 52.dp)
                                            .semantics {
                                                contentDescription = when (slotState) {
                                                    SlotAvailability.SELECTED -> "$slot, selected"
                                                    SlotAvailability.BOOKED_BY_YOU -> "$slot, booked by you"
                                                    SlotAvailability.OCCUPIED -> "$slot, occupied"
                                                    SlotAvailability.PAST -> "$slot, past"
                                                    SlotAvailability.AVAILABLE -> "$slot, available"
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = slot,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val tagLabel = when (slotState) {
                                                    SlotAvailability.BOOKED_BY_YOU -> "Booked by you"
                                                    SlotAvailability.OCCUPIED -> "Occupied"
                                                    SlotAvailability.PAST -> "Passed"
                                                    else -> null
                                                }
                                                if (tagLabel != null) {
                                                    Text(
                                                        text = tagLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = textColor
                                                    )
                                                }
                                            }
                                            when (slotState) {
                                                SlotAvailability.SELECTED -> Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                SlotAvailability.BOOKED_BY_YOU -> Icon(
                                                    imageVector = Icons.Outlined.EventAvailable,
                                                    contentDescription = "Booked by you",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                SlotAvailability.OCCUPIED -> Icon(
                                                    imageVector = Icons.Outlined.Block,
                                                    contentDescription = "Occupied",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                SlotAvailability.PAST -> Icon(
                                                    imageVector = Icons.Outlined.Schedule,
                                                    contentDescription = "Passed",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                SlotAvailability.AVAILABLE -> Unit
                                            }
                                        }
                                    }
                                }
                                if (rowSlots.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Step 4: Cycle Selection
                item(key = "step_cycle") {
                    StepHeader(stepNumber = "4", title = "Select Cycle")
                    Spacer(modifier = Modifier.height(10.dp))

                    val selectedMachine = uiState.selectedMachine
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CycleType.entries.chunked(2).forEach { rowCycles ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCycles.forEach { cycle ->
                                    val isSelected = uiState.selectedCycleType == cycle
                                    val isSupported = selectedMachine?.supportsCycle(cycle) ?: true
                                    val cycleIcon = when (cycle) {
                                        CycleType.QUICK_WASH -> Icons.Default.Bolt
                                        CycleType.STANDARD_WASH -> Icons.Default.LocalLaundryService
                                        CycleType.HEAVY_BEDDING -> Icons.Default.DryCleaning
                                        CycleType.TURBO_DRY -> Icons.Default.Speed
                                    }
                                    val shortName = when (cycle) {
                                        CycleType.QUICK_WASH -> "Quick"
                                        CycleType.STANDARD_WASH -> "Standard"
                                        CycleType.HEAVY_BEDDING -> "Heavy"
                                        CycleType.TURBO_DRY -> "Turbo"
                                    }

                                    Surface(
                                        onClick = { if (isSupported) viewModel.selectCycleType(cycle) },
                                        enabled = isSupported,
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            !isSupported -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = when {
                                                !isSupported -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 56.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = cycleIcon,
                                                contentDescription = null,
                                                tint = when {
                                                    !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = shortName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = when {
                                                        !isSupported -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = if (isSupported) {
                                                        "${cycle.durationMinutes} mins"
                                                    } else {
                                                        "Not supported"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = if (isSupported) 1f else 0.6f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes
                item(key = "notes") {
                    LaundryTextField(
                        value = uiState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        label = stringResource(R.string.notes_optional),
                        placeholder = "e.g., detergent already placed in tray"
                    )
                }

                // Step 5: Compact Booking Summary & Confirmation
                item(key = "step_summary") {
                    StepHeader(stepNumber = "5", title = "Booking Summary")
                    Spacer(modifier = Modifier.height(10.dp))

                    val selectedMachine = uiState.selectedMachine
                    LaundryCard(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        SummaryLine(label = "Date", value = selectedDateFormatted)
                        SummaryDivider()
                        SummaryLine(
                            label = "Machine",
                            value = selectedMachine?.let { "${it.name} (${it.categoryLabel()})" } ?: "Not selected"
                        )
                        SummaryDivider()
                        SummaryLine(
                            label = "Time Slot",
                            value = uiState.selectedTimeSlot.ifBlank { "No slot selected" }
                        )
                        SummaryDivider()
                        SummaryLine(
                            label = "Cycle",
                            value = uiState.selectedCycleType.displayName
                        )
                        SummaryDivider()
                        SummaryLine(
                            label = "Total Duration",
                            value = "${uiState.selectedCycleType.durationMinutes} minutes",
                            highlight = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LaundryButton(
                        text = stringResource(R.string.confirm_slot_booking),
                        onClick = { viewModel.confirmBooking() },
                        enabled = uiState.selectedMachine != null && uiState.selectedTimeSlot.isNotBlank(),
                        isLoading = uiState.result is BookingResult.Loading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // Success Dialog
    showSuccessDialog?.let { booking ->
        val availablePalette = rememberStatusColors(MachineStatus.AVAILABLE)
        val isPendingSync = booking.syncStatus == SyncStatus.PENDING

        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = null
                onBack()
            },
            icon = {
                Icon(
                    imageVector = if (isPendingSync) Icons.Outlined.CloudUpload else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isPendingSync) MaterialTheme.colorScheme.primary else availablePalette.dot,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    text = if (isPendingSync) "Saved Offline (Pending Sync)" else "Slot Reserved!",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPendingSync) {
                            "Your reservation is saved locally and will be confirmed with the hostel server once connected."
                        } else {
                            "Your laundry slot is confirmed and synced."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "BOOKING PASS CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = booking.bookingCode,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${booking.machineName} (${booking.timeSlot})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = "We'll send a reminder 15 minutes before your slot starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = null
                    onBack()
                }) {
                    Text("Done")
                }
            }
        )
    }

    // Error Dialog
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Reservation Error", style = MaterialTheme.typography.titleLarge) },
            text = { Text(error, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun StepHeader(
    stepNumber: String,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}
