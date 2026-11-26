package com.example.aasha.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun MachineFilterSection(
    availableBlocks: List<String>,
    availableFloors: List<Int>,
    selectedBlock: String,
    selectedFloor: Int?,
    selectedTypeFilter: String,
    showAvailableOnly: Boolean,
    hasActiveFilters: Boolean,
    onBlockSelected: (String) -> Unit,
    onFloorSelected: (Int?) -> Unit,
    onTypeSelected: (String) -> Unit,
    onToggleAvailableOnly: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filter_all") {
                LaundryFilterChip(
                    label = "All",
                    selected = selectedBlock == "All" && selectedFloor == null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBlockSelected("All")
                        if (selectedFloor != null) onFloorSelected(null)
                    }
                )
            }
            items(availableBlocks, key = { "block_$it" }) { block ->
                LaundryFilterChip(
                    label = block,
                    selected = selectedBlock.equals(block, ignoreCase = true),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBlockSelected(block)
                    }
                )
            }
            items(availableFloors, key = { "floor_$it" }) { floor ->
                LaundryFilterChip(
                    label = formatFloorName(floor),
                    selected = selectedFloor == floor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFloorSelected(floor)
                    }
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "type_washers") {
                LaundryFilterChip(
                    label = "Washers",
                    selected = selectedTypeFilter == "Washers",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTypeSelected("Washers")
                    }
                )
            }
            item(key = "type_dryers") {
                LaundryFilterChip(
                    label = "Dryers",
                    selected = selectedTypeFilter == "Dryers",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTypeSelected("Dryers")
                    }
                )
            }
            item(key = "available_only") {
                LaundryFilterChip(
                    label = "Available Only",
                    selected = showAvailableOnly,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleAvailableOnly()
                    }
                )
            }
            if (hasActiveFilters) {
                item(key = "clear_filters") {
                    FilterChip(
                        selected = false,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResetFilters()
                        },
                        label = {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            iconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LaundryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = if (selected) {
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
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

fun formatFloorName(floor: Int): String = when (floor) {
    0 -> "Ground Floor"
    1 -> "First Floor"
    2 -> "Second Floor"
    3 -> "Third Floor"
    else -> "Floor $floor"
}
