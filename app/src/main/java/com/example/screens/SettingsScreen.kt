package com.example.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.models.AppSettings
import com.example.models.AppThemeMode
import com.example.models.CursorStyle
import com.example.providers.SettingsViewModel
import com.example.ui.theme.CopperLight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.resolveTerminalFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val knownHosts by settingsViewModel.knownHosts.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var portDraft by remember { mutableStateOf(settings.defaultPort.toString()) }
    var usernameDraft by remember { mutableStateOf(settings.defaultUsername) }
    var fontDropdownExpanded by remember { mutableStateOf(false) }
    var showClearCredsConfirm by remember { mutableStateOf(false) }
    var showClearKnownHostsConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(settings.defaultPort) {
        portDraft = settings.defaultPort.toString()
    }
    LaunchedEffect(settings.defaultUsername) {
        usernameDraft = settings.defaultUsername
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 660.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppThemeMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { settingsViewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = AppThemeMode.entries.size
                                    ),
                                    modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Terminal Emulator",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Column {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Font Size", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${settings.terminalFontSize.roundToInt()} sp",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.terminalFontSize,
                                onValueChange = {
                                    settingsViewModel.setTerminalFontSize(it.roundToInt().toFloat())
                                },
                                valueRange = 10f..24f,
                                steps = 13,
                                modifier = Modifier.testTag("font_size_slider")
                            )
                        }

                        ExposedDropdownMenuBox(
                            expanded = fontDropdownExpanded,
                            onExpandedChange = { fontDropdownExpanded = !fontDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = settings.terminalFontFamily,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Terminal Font Family") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontDropdownExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("font_family_selector")
                            )
                            ExposedDropdownMenu(
                                expanded = fontDropdownExpanded,
                                onDismissRequest = { fontDropdownExpanded = false }
                            ) {
                                AppSettings.AvailableFonts.forEach { family ->
                                    DropdownMenuItem(
                                        text = { Text(family) },
                                        onClick = {
                                            settingsViewModel.setTerminalFontFamily(family)
                                            fontDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Text("Cursor Style", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            CursorStyle.entries.forEachIndexed { idx, style ->
                                SegmentedButton(
                                    selected = settings.cursorStyle == style,
                                    onClick = { settingsViewModel.setCursorStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = CursorStyle.entries.size
                                    ),
                                    modifier = Modifier.testTag("cursor_style_${style.name.lowercase()}")
                                ) {
                                    Text(style.label)
                                }
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFF0C0A09),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    text = "root@forge:~# uptime",
                                    fontFamily = resolveTerminalFontFamily(settings.terminalFontFamily),
                                    fontSize = settings.terminalFontSize.sp,
                                    color = Color(0xFFF2E6DC)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = when (settings.cursorStyle) {
                                        CursorStyle.BLOCK -> Modifier
                                            .width(9.dp)
                                            .height((settings.terminalFontSize * 1.15f).dp)
                                            .background(CopperLight.copy(alpha = 0.8f))
                                        CursorStyle.UNDERLINE -> Modifier
                                            .width(9.dp)
                                            .height(3.dp)
                                            .align(Alignment.Bottom)
                                            .background(CopperLight)
                                        CursorStyle.BAR -> Modifier
                                            .width(2.5.dp)
                                            .height((settings.terminalFontSize * 1.15f).dp)
                                            .background(CopperLight)
                                    }
                                )
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Connection Defaults",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = usernameDraft,
                                onValueChange = {
                                    usernameDraft = it
                                    if (it.isNotBlank()) settingsViewModel.setDefaultUsername(it)
                                },
                                label = { Text("Default Username") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.65f)
                                    .testTag("default_username_input")
                            )

                            OutlinedTextField(
                                value = portDraft,
                                onValueChange = { text ->
                                    val digits = text.filter(Char::isDigit)
                                    portDraft = digits
                                    digits.toIntOrNull()?.let { port ->
                                        if (port in 1..65535) settingsViewModel.setDefaultPort(port)
                                    }
                                },
                                label = { Text("Default Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(0.35f)
                                    .testTag("default_port_input")
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Reconnect", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Automatically retry SSH connection if dropped unexpectedly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoReconnect,
                                onCheckedChange = settingsViewModel::setAutoReconnect,
                                modifier = Modifier.testTag("auto_reconnect_switch")
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Known Host Keys (${knownHosts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (knownHosts.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showClearKnownHostsConfirm = true },
                                    modifier = Modifier.testTag("clear_known_hosts_btn")
                                ) {
                                    Text("Clear All")
                                }
                            }
                        }

                        if (knownHosts.isEmpty()) {
                            Text(
                                text = "No SSH host fingerprints stored yet. Fingerprints are verified on first connect (TOFU).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            knownHosts.forEach { entry ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${entry.endpointKey} (${entry.algorithm})",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = entry.fingerprint,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { settingsViewModel.removeKnownHost(entry.endpointKey) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Revoke ${entry.endpointKey}",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Security Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Wipe all stored passwords, PEM private keys, and passphrases from Android EncryptedSharedPreferences. Host profiles will remain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showClearCredsConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_all_credentials_btn")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear All Credentials")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showClearCredsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCredsConfirm = false },
            title = { Text("Clear All Credentials?") },
            text = {
                Text("This permanently erases every SSH password, private key, and passphrase from the encrypted vault. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCredsConfirm = false
                        settingsViewModel.clearAllCredentials {
                            scope.launch {
                                snackbarHostState.showSnackbar("All encrypted credentials cleared")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_credentials_btn")
                ) {
                    Text("Wipe Vault")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCredsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearKnownHostsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearKnownHostsConfirm = false },
            title = { Text("Clear Known Host Keys?") },
            text = {
                Text("All saved TOFU SSH host fingerprints will be removed. You will be asked to verify each host fingerprint again on next connect.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearKnownHostsConfirm = false
                        settingsViewModel.clearAllKnownHosts {
                            scope.launch {
                                snackbarHostState.showSnackbar("Cleared all known host keys")
                            }
                        }
                    }
                ) {
                    Text("Clear Keys")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearKnownHostsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
