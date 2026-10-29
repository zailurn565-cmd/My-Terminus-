package com.example.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily

private data class ShortcutKeySpec(
    val id: String,
    val label: String,
    val sequence: String? = null,
    val isCtrlModifier: Boolean = false,
    val isAltModifier: Boolean = false
)

private val TerminalKeys = listOf(
    ShortcutKeySpec(id = "esc", label = "Esc", sequence = "\u001b"),
    ShortcutKeySpec(id = "tab", label = "Tab", sequence = "\t"),
    ShortcutKeySpec(id = "ctrl", label = "Ctrl", isCtrlModifier = true),
    ShortcutKeySpec(id = "alt", label = "Alt", isAltModifier = true),
    ShortcutKeySpec(id = "slash", label = "/", sequence = "/"),
    ShortcutKeySpec(id = "dash", label = "-", sequence = "-"),
    ShortcutKeySpec(id = "pipe", label = "|", sequence = "|"),
    ShortcutKeySpec(id = "tilde", label = "~", sequence = "~"),
    ShortcutKeySpec(id = "up", label = "↑", sequence = "\u001b[A"),
    ShortcutKeySpec(id = "down", label = "↓", sequence = "\u001b[B"),
    ShortcutKeySpec(id = "left", label = "←", sequence = "\u001b[D"),
    ShortcutKeySpec(id = "right", label = "→", sequence = "\u001b[C")
)

@Composable
fun KeyboardShortcutBar(
    ctrlActive: Boolean,
    altActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSendSequence: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            TerminalKeys.forEach { key ->
                val isSelected = (key.isCtrlModifier && ctrlActive) ||
                    (key.isAltModifier && altActive)
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when {
                            key.isCtrlModifier -> onToggleCtrl()
                            key.isAltModifier -> onToggleAlt()
                            key.sequence != null -> onSendSequence(key.sequence)
                        }
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("shortcut_key_${key.id}")
                ) {
                    Text(
                        text = key.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPasteClipboard()
                },
                shape = MaterialTheme.shapes.extraSmall,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                    .testTag("shortcut_key_paste")
            ) {
                Text(
                    text = "Paste",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
