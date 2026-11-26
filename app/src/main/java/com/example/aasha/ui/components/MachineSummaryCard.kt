package com.example.aasha.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.ui.theme.LaundrySlotTheme

@Composable
fun MachineSummaryRow(
    availableCount: Int,
    inUseCount: Int,
    maintenanceCount: Int,
    selectedStatus: MachineStatus?,
    onSummaryClick: (MachineStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val availableColors = rememberStatusColors(MachineStatus.AVAILABLE)
    val inUseColors = rememberStatusColors(MachineStatus.IN_USE)
    val maintenanceColors = rememberStatusColors(MachineStatus.OUT_OF_ORDER)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MachineSummaryCard(
            label = "Available",
            count = availableCount,
            icon = Icons.Outlined.CheckCircle,
            containerColor = availableColors.background,
            accentColor = availableColors.text,
            isSelected = selectedStatus == MachineStatus.AVAILABLE,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSummaryClick(MachineStatus.AVAILABLE)
            },
            modifier = Modifier.weight(1f)
        )
        MachineSummaryCard(
            label = "In Use",
            count = inUseCount,
            icon = Icons.Outlined.Autorenew,
            containerColor = inUseColors.background,
            accentColor = inUseColors.text,
            isSelected = selectedStatus == MachineStatus.IN_USE,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSummaryClick(MachineStatus.IN_USE)
            },
            modifier = Modifier.weight(1f)
        )
        MachineSummaryCard(
            label = "Maintenance",
            count = maintenanceCount,
            icon = Icons.Outlined.Build,
            containerColor = maintenanceColors.background,
            accentColor = maintenanceColors.text,
            isSelected = selectedStatus == MachineStatus.OUT_OF_ORDER,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSummaryClick(MachineStatus.OUT_OF_ORDER)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MachineSummaryCard(
    label: String,
    count: Int,
    icon: ImageVector,
    containerColor: Color,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBorder by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(220),
        label = "summary_card_border"
    )
    val animatedBackground by animateColorAsState(
        targetValue = if (isSelected) containerColor else containerColor.copy(alpha = 0.88f),
        animationSpec = tween(220),
        label = "summary_card_bg"
    )

    val selectionLabel = if (isSelected) "selected" else "not selected"
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "$count $label machines, $selectionLabel. Tap to filter."
        },
        shape = RoundedCornerShape(12.dp),
        color = animatedBackground,
        border = if (isSelected) BorderStroke(1.5.dp, animatedBorder) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MachineSummaryRowPreview() {
    LaundrySlotTheme {
        MachineSummaryRow(
            availableCount = 6,
            inUseCount = 2,
            maintenanceCount = 1,
            selectedStatus = MachineStatus.AVAILABLE,
            onSummaryClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
