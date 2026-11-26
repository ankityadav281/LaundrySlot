package com.example.aasha.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aasha.ui.theme.LaundrySlotTheme

enum class LaundryButtonStyle {
    Primary,
    Secondary,
    Outlined,
    Tertiary
}

@Composable
fun LaundryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    style: LaundryButtonStyle = LaundryButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    minHeight: Dp = 48.dp,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    val isInteractive = enabled && !isLoading
    val shape = RoundedCornerShape(cornerRadius)
    val buttonModifier = modifier.defaultMinSize(minHeight = minHeight)

    when (style) {
        LaundryButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
            ) {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    progressColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        LaundryButtonStyle.Secondary -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    progressColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        LaundryButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isInteractive) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    progressColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        LaundryButtonStyle.Tertiary -> {
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = isInteractive,
                shape = shape,
                contentPadding = contentPadding,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                ButtonContent(
                    text = text,
                    isLoading = isLoading,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    progressColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    progressColor: androidx.compose.ui.graphics.Color
) {
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "button_loading_content"
    ) { loading ->
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = progressColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LaundryButtonPreview() {
    LaundrySlotTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LaundryButton(text = "Reserve Slot", onClick = {}, modifier = Modifier.fillMaxWidth())
            LaundryButton(text = "Start Now", onClick = {}, style = LaundryButtonStyle.Secondary)
            LaundryButton(text = "Cancel", onClick = {}, style = LaundryButtonStyle.Outlined)
            LaundryButton(text = "Submitting...", onClick = {}, isLoading = true)
        }
    }
}
