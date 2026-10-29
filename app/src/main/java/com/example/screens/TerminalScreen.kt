package com.example.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.providers.SettingsViewModel
import com.example.providers.SnippetViewModel
import com.example.providers.TerminalUiEvent
import com.example.providers.TerminalViewModel
import com.example.providers.VpsWorkflowStage
import com.example.services.HostKeyPrompt
import com.example.services.SshConnectionState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusConnectedGreen
import com.example.ui.theme.StatusConnectingAmber
import com.example.ui.theme.StatusErrorRed
import com.example.widgets.ConnectButton
import com.example.widgets.KeyboardShortcutBar
import com.example.widgets.SnippetQuickBar
import com.example.widgets.XtermCanvasView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    hostId: String,
    terminalViewModel: TerminalViewModel,
    snippetViewModel: SnippetViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onRedirectToEditCredentials: (String) -> Unit,
    onOpenSnippetsManager: () -> Unit
) {
    val connectionState by terminalViewModel.connectionState.collectAsStateWithLifecycle()
    val hostProfile by terminalViewModel.hostProfile.collectAsStateWithLifecycle()
    val workflowStage by terminalViewModel.workflowStage.collectAsStateWithLifecycle()
    val snapshot by terminalViewModel.terminalSnapshot.collectAsStateWithLifecycle()
    val scrollbackOffset by terminalViewModel.scrollbackOffset.collectAsStateWithLifecycle()
    val ctrlSticky by terminalViewModel.ctrlSticky.collectAsStateWithLifecycle()
    val altSticky by terminalViewModel.altSticky.collectAsStateWithLifecycle()
    val hostKeyPrompt by terminalViewModel.activeHostKeyPrompt.collectAsStateWithLifecycle()
    val activeTmuxSession by terminalViewModel.activeTmuxSessionName.collectAsStateWithLifecycle()
    val activeProjectDir by terminalViewModel.activeProjectDirectory.collectAsStateWithLifecycle()
    val runnableFiles by terminalViewModel.runnableProjectFiles.collectAsStateWithLifecycle()

    val allSnippets by snippetViewModel.snippets.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    val relevantSnippets = remember(allSnippets, hostId) {
        allSnippets.filter { it.hostId == null || it.hostId == hostId }
    }

    var showQuickRunDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    BackHandler {
        val handled = terminalViewModel.handleWorkflowBack()
        if (!handled) {
            terminalViewModel.disconnectUserSession()
            onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            terminalViewModel.disconnectUserSession()
        }
    }

    LaunchedEffect(terminalViewModel) {
        terminalViewModel.events.collect { event ->
            when (event) {
                is TerminalUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is TerminalUiEvent.RedirectMissingCredential -> {
                    onRedirectToEditCredentials(event.hostId)
                }
                TerminalUiEvent.HapticConnected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                TerminalUiEvent.HapticDisconnected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    val isWorkspaceView = workflowStage == VpsWorkflowStage.ACTIVE_TERMINAL ||
        workflowStage == VpsWorkflowStage.FILE_EXPLORER

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val handled = terminalViewModel.handleWorkflowBack()
                                if (!handled) {
                                    terminalViewModel.disconnectUserSession()
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier.testTag("session_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hostProfile?.label ?: "VPS Session",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(hostProfile?.connectionSummary ?: "")
                                    activeTmuxSession?.let { append(" • tmux:$it") }
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isWorkspaceView && connectionState == SshConnectionState.CONNECTED) {
                            FilledTonalButton(
                                onClick = terminalViewModel::openHubHome,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .padding(end = 6.dp)
                                    .testTag("return_to_vps_hub_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dashboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hub", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        SessionStatusBadge(state = connectionState)

                        Spacer(modifier = Modifier.width(4.dp))

                        when (connectionState) {
                            SshConnectionState.CONNECTED -> {
                                IconButton(
                                    onClick = { terminalViewModel.disconnectUserSession() },
                                    modifier = Modifier.testTag("disconnect_session_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LinkOff,
                                        contentDescription = stringResource(R.string.action_disconnect),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            SshConnectionState.DISCONNECTED, SshConnectionState.ERROR -> {
                                ConnectButton(
                                    onClick = { terminalViewModel.connect() },
                                    label = stringResource(R.string.action_reconnect),
                                    isReconnect = true,
                                    testTag = "reconnect_session_btn"
                                )
                            }
                            SshConnectionState.CONNECTING -> Unit
                        }
                    }

                    if (isWorkspaceView) {
                        PrimaryTabRow(
                            selectedTabIndex = if (workflowStage == VpsWorkflowStage.ACTIVE_TERMINAL) 0 else 1,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Tab(
                                selected = workflowStage == VpsWorkflowStage.ACTIVE_TERMINAL,
                                onClick = terminalViewModel::openRawTerminalStage,
                                text = { Text(stringResource(R.string.tab_terminal)) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("session_tab_terminal")
                            )
                            Tab(
                                selected = workflowStage == VpsWorkflowStage.FILE_EXPLORER,
                                onClick = terminalViewModel::openFileExplorerStage,
                                text = { Text(stringResource(R.string.tab_sftp)) },
                                icon = {
                                    Icon(
                                        Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("session_tab_sftp")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            when (workflowStage) {
                VpsWorkflowStage.ACTIVE_TERMINAL -> {
                    if (connectionState == SshConnectionState.CONNECTED) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = activeProjectDir,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        terminalViewModel.refreshRunnableFilesInActiveDir()
                                        showQuickRunDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 38.dp)
                                        .testTag("terminal_quick_run_host_btn")
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Run / Host File", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        XtermCanvasView(
                            engine = terminalViewModel.terminalEngine,
                            snapshot = snapshot,
                            scrollbackOffset = scrollbackOffset,
                            fontSizeSp = settings.terminalFontSize,
                            fontFamilyName = settings.terminalFontFamily,
                            cursorStyle = settings.cursorStyle,
                            onTypedChar = terminalViewModel::sendTypedChar,
                            onSendRaw = terminalViewModel::sendRawSequence,
                            onPasteText = terminalViewModel::pasteText,
                            onResizeGrid = terminalViewModel::onTerminalViewportResized,
                            onScrollHistoryBy = terminalViewModel::scrollHistoryBy,
                            onJumpToBottom = terminalViewModel::jumpToLiveBottom,
                            onClearBuffer = terminalViewModel::clearTerminalBuffer
                        )
                    }

                    SnippetQuickBar(
                        snippets = relevantSnippets,
                        enabled = connectionState == SshConnectionState.CONNECTED,
                        onRunSnippet = { snippet ->
                            terminalViewModel.executeSnippetCommand(snippet.command)
                        },
                        onManageSnippets = onOpenSnippetsManager
                    )

                    KeyboardShortcutBar(
                        ctrlActive = ctrlSticky,
                        altActive = altSticky,
                        onToggleCtrl = terminalViewModel::toggleCtrl,
                        onToggleAlt = terminalViewModel::toggleAlt,
                        onSendSequence = terminalViewModel::sendRawSequence,
                        onPasteClipboard = {
                            val text = clipboardManager.getText()?.text
                            if (!text.isNullOrEmpty()) {
                                terminalViewModel.pasteText(text)
                            }
                        }
                    )
                }

                VpsWorkflowStage.FILE_EXPLORER -> {
                    SftpScreen(
                        terminalViewModel = terminalViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    VpsWorkflowHub(
                        stage = workflowStage,
                        terminalViewModel = terminalViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showQuickRunDialog) {
        AlertDialog(
            onDismissRequest = { showQuickRunDialog = false },
            title = { Text("Run / Host Script") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Auto-installs VPS requirements (--break-system-packages) and runs in tmux ($activeProjectDir):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (runnableFiles.isEmpty()) {
                        Text(
                            text = "No .py, .js, or .sh scripts detected in $activeProjectDir. You can also browse all folders in the Files tab.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.height(220.dp)
                        ) {
                            items(runnableFiles, key = { it.fullPath }) { file ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showQuickRunDialog = false
                                            terminalViewModel.runOrHostFile(file)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = JetBrainsMonoFontFamily
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuickRunDialog = false
                        terminalViewModel.openFileExplorerStage()
                    }
                ) {
                    Text("Browse All Files")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showQuickRunDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (connectionState == SshConnectionState.CONNECTING && hostKeyPrompt == null) {
        AlertDialog(
            onDismissRequest = { /* Require explicit Cancel button */ },
            title = { Text(stringResource(R.string.status_connecting)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Column {
                        Text(
                            text = hostProfile?.label ?: "Establishing SSH transport",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = hostProfile?.connectionSummary ?: "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { terminalViewModel.cancelConnecting() },
                    modifier = Modifier.testTag("cancel_connect_modal_btn")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    hostKeyPrompt?.let { prompt ->
        HostKeyVerificationDialog(
            prompt = prompt,
            onAccept = { terminalViewModel.resolveHostKeyPrompt(true) },
            onReject = { terminalViewModel.resolveHostKeyPrompt(false) }
        )
    }
}

@Composable
private fun SessionStatusBadge(state: SshConnectionState) {
    val (dotColor, text) = when (state) {
        SshConnectionState.CONNECTED -> StatusConnectedGreen to stringResource(R.string.status_connected)
        SshConnectionState.CONNECTING -> StatusConnectingAmber to stringResource(R.string.status_connecting)
        SshConnectionState.DISCONNECTED,
        SshConnectionState.ERROR -> StatusErrorRed to stringResource(R.string.status_disconnected)
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = dotColor.copy(alpha = 0.14f),
        modifier = Modifier.testTag("session_status_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor
            )
        }
    }
}

@Composable
private fun HostKeyVerificationDialog(
    prompt: HostKeyPrompt,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                imageVector = if (prompt.isMismatch) {
                    Icons.Filled.WarningAmber
                } else {
                    Icons.Filled.Security
                },
                contentDescription = null,
                tint = if (prompt.isMismatch) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        title = {
            Text(
                if (prompt.isMismatch) {
                    "Host Key Mismatch Detected!"
                } else {
                    "Verify SSH Host Fingerprint"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (prompt.isMismatch) {
                        "WARNING: The host key presented by ${prompt.host}:${prompt.port} does not match the previously trusted fingerprint. This could indicate a man-in-the-middle attack or a reinstalled server."
                    } else {
                        "First connection to ${prompt.host}:${prompt.port}. Verify the ${prompt.algorithm} fingerprint before trusting this host:"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Presented (${prompt.algorithm}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt.presentedFingerprint,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (prompt.expectedFingerprint != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Previously Trusted:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = prompt.expectedFingerprint,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = if (prompt.isMismatch) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.testTag("accept_host_key_btn")
            ) {
                Text(if (prompt.isMismatch) "Replace & Trust" else "Trust Fingerprint")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.testTag("reject_host_key_btn")
            ) {
                Text("Reject")
            }
        }
    )
}
