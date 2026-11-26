package com.example.aasha.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aasha.domain.model.CycleType
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.categoryLabel
import com.example.aasha.domain.model.typeDisplayName
import com.example.aasha.viewmodel.MachineCardUiModel

private val FaultIssueOptions = listOf(
    "Won't Drain",
    "Door Jammed",
    "Power Issue",
    "Excessive Noise",
    "Water Leakage",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MachineDetailsBottomSheet(
    machineModel: MachineCardUiModel,
    isOnline: Boolean,
    lastSyncTime: Long,
    initiallyExpandFault: Boolean,
    isSubmittingFault: Boolean,
    faultSubmitError: String?,
    onDismiss: () -> Unit,
    onReserveSlot: () -> Unit,
    onQuickStart: () -> Unit,
    onSubmitFaultReport: (selectedCategories: Set<String>, description: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val machine = machineModel.machine

    var faultExpanded by remember(machine.id, initiallyExpandFault) {
        mutableStateOf(initiallyExpandFault)
    }
    var selectedFaults by remember(machine.id) { mutableStateOf(setOf<String>()) }
    var faultDescription by remember(machine.id) { mutableStateOf("") }
    var localValidationError by remember(machine.id) { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    LaunchedEffect(initiallyExpandFault) {
        if (initiallyExpandFault) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmittingFault) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Machine Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalLaundryService,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(26.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = machine.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${machine.id} • ${machine.categoryLabel()} (${machine.typeDisplayName()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                MachineStatusBadge(status = machine.status)
            }

            LaundryCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailInfoItem(
                        icon = Icons.Outlined.LocationOn,
                        label = "Block & Floor",
                        value = "${machine.blockName} • ${formatFloorName(machine.floor)}",
                        modifier = Modifier.weight(1f)
                    )
                    DetailInfoItem(
                        icon = Icons.Outlined.Scale,
                        label = "Capacity",
                        value = "${machine.capacityKg} kg",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section 2: Live Availability (with explicit Live vs Cached indicator)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Availability",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOnline) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (isOnline) {
                                "Live"
                            } else {
                                "Cached • ${formatRelativeSync(lastSyncTime)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                LaundryCard(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailInfoItem(
                                icon = Icons.Outlined.Groups,
                                label = "Current Queue",
                                value = if (machineModel.queueLength == 0) {
                                    "No queue"
                                } else {
                                    "${machineModel.queueLength} scheduled"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            DetailInfoItem(
                                icon = Icons.Outlined.AccessTime,
                                label = "Est. Wait Time",
                                value = when {
                                    machine.status == MachineStatus.OUT_OF_ORDER -> "Unavailable"
                                    machineModel.estimatedWaitMinutes > 0 -> "~${machineModel.estimatedWaitMinutes} min"
                                    else -> "Ready now"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailInfoItem(
                            icon = Icons.Outlined.CalendarToday,
                            label = "Next Available Time",
                            value = machineModel.nextAvailableSlot,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!machine.currentCycleName.isNullOrBlank() && machine.status == MachineStatus.IN_USE) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DetailInfoItem(
                                icon = Icons.Outlined.LocalLaundryService,
                                label = "Current Cycle",
                                value = machine.currentCycleName,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Section 3: Supported Wash Cycles (only those supported by this machine)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Supported Wash Cycles",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    machineModel.supportedCycles.forEach { cycle ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = formatCycleDurationLabel(cycle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Primary / Secondary Actions
            if (machine.status != MachineStatus.OUT_OF_ORDER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (machine.status == MachineStatus.AVAILABLE) {
                        LaundryButton(
                            text = "Start Now",
                            onClick = onQuickStart,
                            enabled = isOnline,
                            style = LaundryButtonStyle.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    LaundryButton(
                        text = "Reserve Slot",
                        onClick = onReserveSlot,
                        style = LaundryButtonStyle.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 4: Fault Reporting
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        faultExpanded = !faultExpanded
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Report a Machine Issue",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select issues to notify maintenance staff",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (faultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (faultExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = faultExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FaultIssueOptions.forEach { category ->
                                val isSelected = category in selectedFaults
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSubmittingFault) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            localValidationError = null
                                            selectedFaults = if (isSelected) {
                                                selectedFaults - category
                                            } else {
                                                selectedFaults + category
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                            }
                        }

                        LaundryTextField(
                            value = faultDescription,
                            onValueChange = {
                                faultDescription = it
                                localValidationError = null
                            },
                            label = "Additional details (optional)",
                            placeholder = "e.g., loud vibration during spin cycle"
                        )

                        val displayedError = localValidationError ?: faultSubmitError
                        displayedError?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { faultExpanded = false },
                                enabled = !isSubmittingFault
                            ) {
                                Text("Cancel")
                            }
                            LaundryButton(
                                text = "Submit Report",
                                onClick = {
                                    when {
                                        selectedFaults.isEmpty() -> {
                                            localValidationError = "Please select at least one issue."
                                        }
                                        (selectedFaults == setOf("Other")) && faultDescription.isBlank() -> {
                                            localValidationError = "Please add a short description for Other."
                                        }
                                        else -> {
                                            onSubmitFaultReport(selectedFaults, faultDescription)
                                        }
                                    }
                                },
                                isLoading = isSubmittingFault,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatCycleDurationLabel(cycle: CycleType): String = when (cycle) {
    CycleType.QUICK_WASH -> "Quick – 30 minutes"
    CycleType.STANDARD_WASH -> "Standard – 45 minutes"
    CycleType.HEAVY_BEDDING -> "Heavy – 60 minutes"
    CycleType.TURBO_DRY -> "Turbo – 40 minutes"
}

private fun formatRelativeSync(lastSyncMillis: Long): String {
    if (lastSyncMillis <= 0L) return "not synced"
    return DateUtils.getRelativeTimeSpanString(
        lastSyncMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
}
