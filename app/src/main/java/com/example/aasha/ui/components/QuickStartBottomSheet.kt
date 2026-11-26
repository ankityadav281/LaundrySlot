package com.example.aasha.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.WashingMachine
import com.example.aasha.domain.model.supportsCycle
import com.example.aasha.domain.model.typeDisplayName

private data class QuickStartCycleOption(
    val cycleType: CycleType,
    val title: String,
    val durationLabel: String,
    val description: String,
    val icon: ImageVector
)

private fun buildCycleOptions(machineType: MachineType): List<QuickStartCycleOption> {
    val isDryer = machineType == MachineType.DRYER
    return listOf(
        QuickStartCycleOption(
            cycleType = CycleType.QUICK_WASH,
            title = "Quick",
            durationLabel = "30 minutes",
            description = if (isDryer) "Short drying cycle." else "Faster washing cycle.",
            icon = Icons.Default.Bolt
        ),
        QuickStartCycleOption(
            cycleType = CycleType.STANDARD_WASH,
            title = "Standard",
            durationLabel = "45 minutes",
            description = if (isDryer) "Regular drying cycle." else "Regular washing cycle.",
            icon = Icons.Default.LocalLaundryService
        ),
        QuickStartCycleOption(
            cycleType = CycleType.HEAVY_BEDDING,
            title = "Heavy",
            durationLabel = "60 minutes",
            description = if (isDryer) "Intensive drying cycle." else "Intensive washing cycle.",
            icon = Icons.Default.DryCleaning
        ),
        QuickStartCycleOption(
            cycleType = CycleType.TURBO_DRY,
            title = "Turbo",
            durationLabel = "40 minutes",
            description = if (isDryer) "Faster drying cycle." else "Faster washing cycle.",
            icon = Icons.Default.Speed
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartBottomSheet(
    machine: WashingMachine,
    isOnline: Boolean,
    isStarting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onStartWashNow: (CycleType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val isDryer = machine.type == MachineType.DRYER
    val cycleOptions = remember(machine.type) { buildCycleOptions(machine.type) }

    val initialSelection = remember(machine.id) {
        if (machine.supportsCycle(CycleType.STANDARD_WASH)) {
            CycleType.STANDARD_WASH
        } else {
            CycleType.TURBO_DRY
        }
    }
    var selectedCycle by remember(machine.id) { mutableStateOf(initialSelection) }

    val isAvailable = machine.status == MachineStatus.AVAILABLE
    val isCycleSupported = machine.supportsCycle(selectedCycle)
    val canStart = isAvailable && isOnline && isCycleSupported

    ModalBottomSheet(
        onDismissRequest = { if (!isStarting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isDryer) "Quick Start Dry" else "Quick Start Wash",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isDryer) {
                        "Choose your preferred drying cycle."
                    } else {
                        "Choose your preferred washing cycle."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selected Machine Info Card
            LaundryCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = machine.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${machine.id} • ${machine.blockName} • ${formatFloorName(machine.floor)} • ${machine.capacityKg} kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = machine.typeDisplayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    MachineStatusBadge(status = machine.status)
                }
            }

            // Cycle Cards (only supported cycles are selectable)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cycleOptions.forEach { option ->
                    val supported = machine.supportsCycle(option.cycleType)
                    val isSelected = selectedCycle == option.cycleType

                    val containerColor by animateColorAsState(
                        targetValue = when {
                            !supported -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(200),
                        label = "cycle_card_bg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = when {
                            !supported -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = tween(200),
                        label = "cycle_card_border"
                    )

                    Surface(
                        onClick = {
                            if (!isStarting && supported) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCycle = option.cycleType
                            }
                        },
                        enabled = supported && !isStarting,
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor,
                        border = BorderStroke(if (isSelected && supported) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected && supported) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected && supported) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (supported) 1f else 0.5f
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(20.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (supported) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        }
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = option.durationLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!supported) {
                                        Text(
                                            text = "Not supported",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (supported) 1f else 0.7f
                                    )
                                )
                            }

                            if (supported) {
                                Icon(
                                    imageVector = if (isSelected) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Validation / Error / Offline Message
            val statusNotice = when {
                errorMessage != null -> errorMessage
                !isOnline -> "You are offline. Reconnect to start a cycle."
                !isAvailable -> "This machine is currently ${machine.status.name.lowercase().replace('_', ' ')} and cannot be started."
                !isCycleSupported -> "${machine.name} does not support this cycle."
                else -> null
            }

            AnimatedVisibility(visible = statusNotice != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusNotice.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            LaundryButton(
                text = if (isDryer) "Start Dry Now" else "Start Wash Now",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onStartWashNow(selectedCycle)
                },
                enabled = canStart,
                isLoading = isStarting,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
