package de.teufel.openmynd.modules.screens.controlDevices.composables.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Common UI building blocks reused across control device feature composables.
 */

@Composable
fun FeatureSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        if (checked != null) {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
fun RangeSliderWithHeader(
    title: String,
    currentValue: Int?,
    range: IntRange,
    valueFormatter: (Int) -> String = { it.toString() },
    onValueCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserAdjusting by remember { mutableStateOf(false) }
    var userValue by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        FeatureSectionHeader(
            title = title,
            trailingContent = {
                if (currentValue != null) {
                    Text(
                        text = valueFormatter(userValue ?: currentValue),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (currentValue != null) {
            LaunchedEffect(currentValue) {
                if (!isUserAdjusting) {
                    userValue = currentValue
                }
            }

            Slider(
                value = (userValue ?: currentValue).toFloat(),
                onValueChange = { newValue ->
                    isUserAdjusting = true
                    userValue = newValue.toInt()
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first).coerceAtLeast(1) - 1,
                onValueChangeFinished = {
                    isUserAdjusting = false
                    val committed = userValue ?: return@Slider
                    onValueCommitted(committed)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${range.first}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "${range.last}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}


