package com.example.aasha.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.domain.model.MachineType
import com.example.aasha.domain.model.typeDisplayName
import com.example.aasha.viewmodel.MachineCardUiModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MachineCard(
    model: MachineCardUiModel,
    isOnline: Boolean,
    onCardClick: () -> Unit,
    onReserveSlotClick: () -> Unit,
    onStartNowClick: () -> Unit,
    onViewDetailsClick: () -> Unit,
    onReportFaultClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val machine = model.machine
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember(machine.id) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Machine identity, Status badge, and Three-dot Overflow Menu
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
                    val isDryer = machine.type == MachineType.DRYER
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDryer) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalLaundryService,
                            contentDescription = null,
                            tint = if (isDryer) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = machine.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${machine.id} • ${machine.typeDisplayName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${machine.blockName} • ${formatFloorName(machine.floor)} • ${machine.capacityKg} kg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MachineStatusBadge(status = machine.status)

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options for ${machine.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Details") },
                                onClick = {
                                    menuExpanded = false
                                    onViewDetailsClick()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Report Fault",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onReportFaultClick()
                                }
                            )
                        }
                    }
                }
            }

            // Availability & Queue Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = when (machine.status) {
                                MachineStatus.AVAILABLE -> if (isOnline) "Available now" else "Cached: Available"
                                MachineStatus.IN_USE -> "Free in ~${model.estimatedWaitMinutes} min"
                                MachineStatus.RESERVED -> "Next open: ${model.nextAvailableSlot}"
                                MachineStatus.OUT_OF_ORDER -> machine.currentUserRoomOrName ?: "Under maintenance"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (model.queueLength > 0) {
                        Text(
                            text = "${model.queueLength} in queue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (machine.status != MachineStatus.OUT_OF_ORDER) {
                        Text(
                            text = "Next: ${model.nextAvailableSlot}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

            // Primary ("Reserve Slot") and Secondary ("Start Now") Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LaundryButton(
                    text = "Start Now",
                    onClick = onStartNowClick,
                    enabled = machine.status == MachineStatus.AVAILABLE && isOnline,
                    style = LaundryButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                LaundryButton(
                    text = "Reserve Slot",
                    onClick = onReserveSlotClick,
                    enabled = machine.status != MachineStatus.OUT_OF_ORDER,
                    style = LaundryButtonStyle.Primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
