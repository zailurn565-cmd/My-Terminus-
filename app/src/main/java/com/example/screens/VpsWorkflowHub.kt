package com.example.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.providers.TerminalViewModel
import com.example.providers.VpsWorkflowStage
import com.example.services.TmuxSessionInfo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusConnectedGreen

@Composable
fun VpsWorkflowHub(
    stage: VpsWorkflowStage,
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val homeDir by terminalViewModel.homeDirectory.collectAsStateWithLifecycle()
    val remotePath by terminalViewModel.remotePath.collectAsStateWithLifecycle()
    val remoteEntries by terminalViewModel.remoteEntries.collectAsStateWithLifecycle()
    val isSftpLoading by terminalViewModel.isSftpLoading.collectAsStateWithLifecycle()
    val sftpTransfer by terminalViewModel.sftpTransfer.collectAsStateWithLifecycle()
    val zipDeploy by terminalViewModel.zipDeployState.collectAsStateWithLifecycle()
    val tmuxSessions by terminalViewModel.tmuxSessions.collectAsStateWithLifecycle()
    val isTmuxLoading by terminalViewModel.isTmuxLoading.collectAsStateWithLifecycle()

    var pendingExistingTargetFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolderDraft by rememberSaveable { mutableStateOf("") }

    val existingFolderZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val folder = pendingExistingTargetFolder ?: remotePath
        if (uri != null) {
            terminalViewModel.uploadProjectZipToFolder(
                targetFolder = folder,
                zipUri = uri,
                createFolderFirst = false
            )
        }
    }

    val newFolderZipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && newFolderDraft.isNotBlank()) {
            terminalViewModel.createNewProjectFolderAndUploadZip(
                parentDirectory = homeDir,
                newFolderName = newFolderDraft,
                zipUri = uri
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (stage) {
            VpsWorkflowStage.HUB_HOME -> {
                HubHomeContent(
                    onChooseUpload = terminalViewModel::selectUploadFlow,
                    onChooseOpenTerminal = terminalViewModel::selectOpenTerminalFlow
                )
            }

            VpsWorkflowStage.UPLOAD_FOLDER_MODE -> {
                StepContainer(
                    title = "Upload Project (.zip)",
                    subtitle = "Choose destination folder on your VPS",
                    onBack = { terminalViewModel.handleWorkflowBack() }
                ) {
                    ActionChoiceCard(
                        icon = Icons.Filled.FolderOpen,
                        title = "Open Existing Folder",
                        subtitle = "ရှိပြီးသား Folder ရွေးပြီး .zip ဖိုင်တင်မည်",
                        onClick = terminalViewModel::selectExistingFolderUploadMode,
                        testTag = "upload_existing_folder_card"
                    )
                    ActionChoiceCard(
                        icon = Icons.Filled.CreateNewFolder,
                        title = "Create New Folder",
                        subtitle = "Folder အသစ်ဆောက်ပြီး .zip ဖိုင်တင်မည်",
                        onClick = {
                            newFolderDraft = ""
                            terminalViewModel.selectNewFolderUploadMode()
                        },
                        testTag = "upload_new_folder_card"
                    )
                }
            }

            VpsWorkflowStage.UPLOAD_PICK_EXISTING_FOLDER -> {
                ExistingFolderPickerContent(
                    currentPath = remotePath,
                    folders = remember(remoteEntries) { remoteEntries.filter { it.isDirectory } },
                    isLoading = isSftpLoading,
                    onBack = { terminalViewModel.handleWorkflowBack() },
                    onNavigateUp = terminalViewModel::navigateSftpUp,
                    onOpenFolder = { terminalViewModel.navigateSftpTo(it) },
                    onSelectFolderForZip = { chosenFolder ->
                        pendingExistingTargetFolder = chosenFolder
                        existingFolderZipPicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    }
                )
            }

            VpsWorkflowStage.UPLOAD_CREATE_NEW_FOLDER -> {
                StepContainer(
                    title = "Create New Folder & Upload .zip",
                    subtitle = "Folder will be created inside $homeDir",
                    onBack = { terminalViewModel.handleWorkflowBack() }
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(18.dp)
                        ) {
                            OutlinedTextField(
                                value = newFolderDraft,
                                onValueChange = { newFolderDraft = it },
                                label = { Text("New Folder Name (e.g. my-bot)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_folder_name_input")
                            )

                            Text(
                                text = "Target path: $homeDir/${newFolderDraft.trim().trim('/')}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(
                                onClick = {
                                    newFolderZipPicker.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                enabled = newFolderDraft.trim().isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 50.dp)
                                    .testTag("pick_zip_for_new_folder_btn")
                            ) {
                                Icon(Icons.Filled.Archive, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select .zip File to Upload")
                            }
                        }
                    }
                }
            }

            VpsWorkflowStage.UPLOAD_TMUX_AND_UNZIP -> {
                TmuxAndAutoUnzipContent(
                    deployState = zipDeploy,
                    transferProgress = sftpTransfer?.progress,
                    onBack = { terminalViewModel.handleWorkflowBack() },
                    onStartTmuxAndUnzip = { sessionName ->
                        terminalViewModel.completeTmuxCreationAndAutoUnzip(sessionName)
                    }
                )
            }

            VpsWorkflowStage.OPEN_TERMINAL_CHOICE -> {
                StepContainer(
                    title = "Open Terminal",
                    subtitle = "Choose running tmux sessions or inspect VPS files",
                    onBack = { terminalViewModel.handleWorkflowBack() }
                ) {
                    ActionChoiceCard(
                        icon = Icons.Filled.Layers,
                        title = "Running Tmux Sessions",
                        subtitle = "Run ထားတဲ့ tmux session အားလုံးကိုကြည့်ပြီး တိုက်ရိုက်ဝင်မည်",
                        onClick = terminalViewModel::openTmuxSessionsPicker,
                        testTag = "choice_tmux_sessions_card"
                    )
                    ActionChoiceCard(
                        icon = Icons.Filled.FolderOpen,
                        title = "View VPS Files",
                        subtitle = "VPS ထဲရှိ Folder နှင့် File များကို ကြည့်မည် / Run & Host လုပ်မည်",
                        onClick = terminalViewModel::openFileExplorerStage,
                        testTag = "choice_view_files_card"
                    )
                    OutlinedButton(
                        onClick = terminalViewModel::openRawTerminalStage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("choice_raw_shell_btn")
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Direct Shell")
                    }
                }
            }

            VpsWorkflowStage.TMUX_SESSION_LIST -> {
                TmuxSessionsListContent(
                    sessions = tmuxSessions,
                    isLoading = isTmuxLoading,
                    onBack = { terminalViewModel.handleWorkflowBack() },
                    onRefresh = terminalViewModel::refreshTmuxSessions,
                    onAttachSession = { session ->
                        terminalViewModel.attachToTmuxSession(session.name, session.path)
                    },
                    onCreateNewSession = { newName ->
                        terminalViewModel.attachToTmuxSession(newName, null)
                    },
                    onKillSession = { session ->
                        terminalViewModel.killTmuxSession(session.name)
                    }
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun HubHomeContent(
    onChooseUpload: () -> Unit,
    onChooseOpenTerminal: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "VPS Connected",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Select what you want to do on this server:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        ActionChoiceCard(
            icon = Icons.Filled.UploadFile,
            title = "Upload File",
            subtitle = "Existing သို့မဟုတ် New Folder ရွေးပြီး .zip တင်မည် • Tmux session အသစ်ဆောက်ပြီး Auto-Unzip လုပ်မည်",
            onClick = onChooseUpload,
            testTag = "hub_upload_file_card"
        )

        ActionChoiceCard(
            icon = Icons.Filled.Terminal,
            title = "Open Terminal",
            subtitle = "Run ထားသော Tmux Session များကို ဝင်ကြည့်မည် သို့မဟုတ် VPS ဖိုင်များကို ကြည့်ရှုပြီး Run / Host လုပ်မည်",
            onClick = onChooseOpenTerminal,
            testTag = "hub_open_terminal_card"
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "Smart Auto-Requirements (--break-system-packages)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Whenever you run or host a script (.py, .js, .sh), ForgeShell automatically installs requirements.txt and missing imports with --break-system-packages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepContainer(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("workflow_step_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        content()
    }
}

@Composable
private fun ActionChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ExistingFolderPickerContent(
    currentPath: String,
    folders: List<com.example.services.RemoteFileEntry>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onSelectFolderForZip: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Select Existing Folder", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onNavigateUp,
                enabled = currentPath != "/"
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent directory")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onSelectFolderForZip(currentPath) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("select_current_folder_zip_btn")
        ) {
            Icon(Icons.Filled.Archive, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Upload .zip Into \"$currentPath\"")
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No subfolders inside $currentPath. Tap the button above to upload here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(folders, key = { it.fullPath }) { folder ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFolder(folder.fullPath) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onSelectFolderForZip(folder.fullPath) }) {
                                Text("Upload .zip")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TmuxAndAutoUnzipContent(
    deployState: com.example.providers.ZipDeployState,
    transferProgress: Float?,
    onBack: () -> Unit,
    onStartTmuxAndUnzip: (String) -> Unit
) {
    var sessionName by remember(deployState.suggestedSessionName) {
        mutableStateOf(deployState.suggestedSessionName)
    }

    LaunchedEffect(deployState.suggestedSessionName) {
        if (sessionName.isBlank()) {
            sessionName = deployState.suggestedSessionName
        }
    }

    StepContainer(
        title = "Create Tmux Session & Auto-Unzip",
        subtitle = deployState.targetFolder,
        onBack = onBack
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = deployState.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (transferProgress != null) {
                    LinearProgressIndicator(
                        progress = { transferProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(transferProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (deployState.isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (deployState.uploadedZipPath != null) {
                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = { sessionName = it },
                        enabled = !deployState.isBusy,
                        label = { Text("New Tmux Session Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tmux_session_name_input")
                    )

                    Button(
                        onClick = { onStartTmuxAndUnzip(sessionName) },
                        enabled = !deployState.isBusy && sessionName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .testTag("start_tmux_and_unzip_btn")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Tmux, Auto-Unzip & Open Terminal")
                    }
                }
            }
        }
    }
}

@Composable
private fun TmuxSessionsListContent(
    sessions: List<TmuxSessionInfo>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAttachSession: (TmuxSessionInfo) -> Unit,
    onCreateNewSession: (String) -> Unit,
    onKillSession: (TmuxSessionInfo) -> Unit
) {
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var newSessionName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Running Tmux Sessions", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Tap any session to attach immediately",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh sessions")
            }
            Button(
                onClick = {
                    newSessionName = ""
                    showNewSessionDialog = true
                },
                modifier = Modifier.testTag("new_tmux_session_btn")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            sessions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No active tmux sessions found on this VPS.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                newSessionName = "main"
                                showNewSessionDialog = true
                            }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start New Tmux Session")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("tmux_sessions_list")
                ) {
                    items(sessions, key = { it.name }) { session ->
                        Surface(
                            onClick = { onAttachSession(session) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tmux_item_${session.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Layers,
                                    contentDescription = null,
                                    tint = if (session.attached) {
                                        StatusConnectedGreen
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = buildString {
                                            append("${session.windows} window(s)")
                                            append(" • ")
                                            append(if (session.attached) "Attached" else "Running")
                                            if (session.path.isNotBlank()) {
                                                append(" • ${session.path}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onKillSession(session) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Kill session ${session.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("New Tmux Session") },
            text = {
                OutlinedTextField(
                    value = newSessionName,
                    onValueChange = { newSessionName = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSessionName.isNotBlank()) {
                            val target = newSessionName
                            showNewSessionDialog = false
                            onCreateNewSession(target)
                        }
                    },
                    enabled = newSessionName.isNotBlank()
                ) {
                    Text("Create & Attach")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
