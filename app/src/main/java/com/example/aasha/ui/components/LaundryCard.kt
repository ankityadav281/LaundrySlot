package com.example.aasha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aasha.ui.theme.LaundrySlotTheme

enum class LaundryCardVariant {
    Standard,
    Interactive,
    Elevated
}

@Composable
fun LaundryCard(
    modifier: Modifier = Modifier,
    variant: LaundryCardVariant = LaundryCardVariant.Standard,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val resolvedBorder = border ?: if (variant != LaundryCardVariant.Elevated) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    } else {
        null
    }

    when {
        variant == LaundryCardVariant.Elevated && onClick != null -> {
            ElevatedCard(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        variant == LaundryCardVariant.Elevated -> {
            ElevatedCard(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        onClick != null -> {
            Card(
                onClick = onClick,
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 1.dp
                ),
                border = resolvedBorder
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }

        else -> {
            Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = resolvedBorder
            ) {
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LaundryCardPreview() {
    LaundrySlotTheme {
        LaundryCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Washer A1", style = MaterialTheme.typography.titleMedium)
            Text("Block A • Floor 1", style = MaterialTheme.typography.bodySmall)
        }
    }
}
