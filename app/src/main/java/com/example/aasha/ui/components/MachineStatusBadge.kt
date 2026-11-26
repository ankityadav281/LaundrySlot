package com.example.aasha.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aasha.domain.model.BookingStatus
import com.example.aasha.domain.model.MachineStatus
import com.example.aasha.ui.theme.LaundrySlotTheme
import com.example.aasha.ui.theme.StatusAvailableBg
import com.example.aasha.ui.theme.StatusAvailableBgDark
import com.example.aasha.ui.theme.StatusAvailableDot
import com.example.aasha.ui.theme.StatusAvailableDotDark
import com.example.aasha.ui.theme.StatusAvailableText
import com.example.aasha.ui.theme.StatusAvailableTextDark
import com.example.aasha.ui.theme.StatusInUseBg
import com.example.aasha.ui.theme.StatusInUseBgDark
import com.example.aasha.ui.theme.StatusInUseDot
import com.example.aasha.ui.theme.StatusInUseDotDark
import com.example.aasha.ui.theme.StatusInUseText
import com.example.aasha.ui.theme.StatusInUseTextDark
import com.example.aasha.ui.theme.StatusOutBg
import com.example.aasha.ui.theme.StatusOutBgDark
import com.example.aasha.ui.theme.StatusOutDot
import com.example.aasha.ui.theme.StatusOutDotDark
import com.example.aasha.ui.theme.StatusOutText
import com.example.aasha.ui.theme.StatusOutTextDark
import com.example.aasha.ui.theme.StatusReservedBg
import com.example.aasha.ui.theme.StatusReservedBgDark
import com.example.aasha.ui.theme.StatusReservedDot
import com.example.aasha.ui.theme.StatusReservedDotDark
import com.example.aasha.ui.theme.StatusReservedText
import com.example.aasha.ui.theme.StatusReservedTextDark

data class StatusColors(
    val background: Color,
    val text: Color,
    val dot: Color,
    val label: String
)

@Composable
fun rememberStatusColors(status: MachineStatus, darkTheme: Boolean = isSystemInDarkTheme()): StatusColors {
    return when (status) {
        MachineStatus.AVAILABLE -> StatusColors(
            background = if (darkTheme) StatusAvailableBgDark else StatusAvailableBg,
            text = if (darkTheme) StatusAvailableTextDark else StatusAvailableText,
            dot = if (darkTheme) StatusAvailableDotDark else StatusAvailableDot,
            label = "Available"
        )
        MachineStatus.IN_USE -> StatusColors(
            background = if (darkTheme) StatusInUseBgDark else StatusInUseBg,
            text = if (darkTheme) StatusInUseTextDark else StatusInUseText,
            dot = if (darkTheme) StatusInUseDotDark else StatusInUseDot,
            label = "In Use"
        )
        MachineStatus.RESERVED -> StatusColors(
            background = if (darkTheme) StatusReservedBgDark else StatusReservedBg,
            text = if (darkTheme) StatusReservedTextDark else StatusReservedText,
            dot = if (darkTheme) StatusReservedDotDark else StatusReservedDot,
            label = "Reserved"
        )
        MachineStatus.OUT_OF_ORDER -> StatusColors(
            background = if (darkTheme) StatusOutBgDark else StatusOutBg,
            text = if (darkTheme) StatusOutTextDark else StatusOutText,
            dot = if (darkTheme) StatusOutDotDark else StatusOutDot,
            label = "Maintenance"
        )
    }
}

@Composable
fun rememberBookingStatusColors(
    status: BookingStatus,
    isExpired: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme()
): StatusColors {
    if (isExpired && status == BookingStatus.UPCOMING) {
        return StatusColors(
            background = MaterialTheme.colorScheme.surfaceVariant,
            text = MaterialTheme.colorScheme.onSurfaceVariant,
            dot = MaterialTheme.colorScheme.outline,
            label = "Expired"
        )
    }

    return when (status) {
        BookingStatus.ACTIVE -> StatusColors(
            background = if (darkTheme) StatusInUseBgDark else StatusInUseBg,
            text = if (darkTheme) StatusInUseTextDark else StatusInUseText,
            dot = if (darkTheme) StatusInUseDotDark else StatusInUseDot,
            label = "Active"
        )
        BookingStatus.UPCOMING -> StatusColors(
            background = if (darkTheme) StatusReservedBgDark else StatusReservedBg,
            text = if (darkTheme) StatusReservedTextDark else StatusReservedText,
            dot = if (darkTheme) StatusReservedDotDark else StatusReservedDot,
            label = "Upcoming"
        )
        BookingStatus.COMPLETED -> StatusColors(
            background = if (darkTheme) StatusAvailableBgDark else StatusAvailableBg,
            text = if (darkTheme) StatusAvailableTextDark else StatusAvailableText,
            dot = if (darkTheme) StatusAvailableDotDark else StatusAvailableDot,
            label = "Completed"
        )
        BookingStatus.CANCELLED -> StatusColors(
            background = if (darkTheme) StatusOutBgDark else StatusOutBg,
            text = if (darkTheme) StatusOutTextDark else StatusOutText,
            dot = if (darkTheme) StatusOutDotDark else StatusOutDot,
            label = "Cancelled"
        )
    }
}

@Composable
fun MachineStatusBadge(
    status: MachineStatus,
    modifier: Modifier = Modifier,
    animateIndicator: Boolean = true
) {
    val palette = rememberStatusColors(status)
    StatusBadgePill(
        palette = palette,
        pulse = animateIndicator && status == MachineStatus.IN_USE,
        modifier = modifier
    )
}

@Composable
fun BookingStatusBadge(
    status: BookingStatus,
    modifier: Modifier = Modifier,
    isExpired: Boolean = false,
    animateIndicator: Boolean = true
) {
    val palette = rememberBookingStatusColors(status = status, isExpired = isExpired)
    StatusBadgePill(
        palette = palette,
        pulse = animateIndicator && status == BookingStatus.ACTIVE,
        modifier = modifier
    )
}

@Composable
private fun StatusBadgePill(
    palette: StatusColors,
    pulse: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = palette.background,
        animationSpec = tween(250),
        label = "status_bg"
    )
    val animatedText by animateColorAsState(
        targetValue = palette.text,
        animationSpec = tween(250),
        label = "status_text"
    )

    val dotScale = if (pulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_pulse_scale"
        )
        scale
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBg)
            .semantics { contentDescription = "Status: ${palette.label}" }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .scale(dotScale)
                .clip(CircleShape)
                .background(palette.dot)
        )
        Text(
            text = palette.label,
            color = animatedText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MachineStatusBadgePreview() {
    LaundrySlotTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            MachineStatusBadge(MachineStatus.AVAILABLE)
            MachineStatusBadge(MachineStatus.IN_USE)
            MachineStatusBadge(MachineStatus.OUT_OF_ORDER)
        }
    }
}
