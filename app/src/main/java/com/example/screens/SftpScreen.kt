package com.example.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.providers.TerminalViewModel
import com.example.services.RemoteFileEntry
import com.example.services.SshConnectionState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.Validators

@Composable
fun SftpScreen(
    terminalViewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by terminalViewModel.connectionState.collectAsStateWithLifecycle()
    val remotePath by terminalViewModel.remotePath.collectAsStateWithLifecycle()
    val entries by terminalViewModel.remoteEntries.collectAsStateWithLifecycle()
    val isLoading by terminalViewModel.isSftpLoading.collectAsStateWithLifecycle()
    val transferState by terminalViewModel.sftpTransfer.collectAsStateWithLifecycle()
    val filePreview by terminalViewModel.filePreview.collectAsStateWithLifecycle()

    var pendingUploadTargetDir by remember { mutableStateOf<String?>(null) }
    var createFolderParentDir by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var renameText by remember { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<RemoteFileEntry?>(null) }
    var permissionsTarget by remember { mutableStateOf<RemoteFileEntry?>(null) }

    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val dir = pendingUploadTargetDir ?: remotePath
        pendingUploadTargetDir = null
        if (uri != null) {
            terminalViewModel.uploadFromUri(uri, dir)
        }
    }

    if (connectionState != SshConnectionState.CONNECTED) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Connect SSH session to browse remote VPS files.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { terminalViewModel.connect() }) {
                    Text("Reconnect Now")
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = { terminalViewModel.navigateSftpUp() },
                    enabled = remotePath != "/",
                    modifier = Modifier.testTag("sftp_up_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Navigate to parent folder"
                    )
                }

                BreadcrumbBar(
                    path = remotePath,
                    onSelectPath = { terminalViewModel.navigateSftpTo(it) },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        newFolderName = ""
                        createFolderParentDir = remotePath
                    },
                    modifier = Modifier.testTag("sftp_mkdir_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "Create remote folder"
                    )
                }

                IconButton(
                    onClick = {
                        pendingUploadTargetDir = remotePath
                        uploadPicker.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.testTag("sftp_upload_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Upload local file"
                    )
                }

                IconButton(
                    onClick = { terminalViewModel.refreshSftp() },
                    modifier = Modifier.testTag("sftp_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh directory"
                    )
                }
            }
        }

        transferState?.let { transfer ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${if (transfer.isUpload) "Uploading" else "Downloading"} ${transfer.fileName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(transfer.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

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
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Directory is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("sftp_entries_list")
                ) {
                    items(
                        items = entries,
                        key = { it.fullPath }
                    ) { entry ->
                        SftpEntryRow(
                            entry = entry,
                            onTap = {
                                if (entry.isDirectory) {
                                    terminalViewModel.navigateSftpTo(entry.fullPath)
                                } else {
                                    terminalViewModel.openFilePreview(entry)
                                }
                            },
                            onRunOrHost = { terminalViewModel.runOrHostFile(entry) },
                            onUnzipHere = { terminalViewModel.unzipRemoteArchiveInExplorer(entry) },
                            onDownload = { terminalViewModel.downloadRemoteFile(entry) },
                            onUploadIntoFolder = {
                                pendingUploadTargetDir = entry.fullPath
                                uploadPicker.launch(arrayOf("*/*"))
                            },
                            onCreateFolderInside = {
                                newFolderName = ""
                                createFolderParentDir = entry.fullPath
                            },
                            onRename = {
                                renameText = entry.name
                                renameTarget = entry
                            },
                            onViewPermissions = { permissionsTarget = entry },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                }
            }
        }
    }

    filePreview?.let { preview ->
        val entry = preview.entry
        val isZip = entry.name.endsWith(".zip", ignoreCase = true)
        AlertDialog(
            onDismissRequest = terminalViewModel::dismissFilePreview,
            title = {
                Column {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = entry.fullPath,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (preview.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 320.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = preview.content.ifEmpty { "[Empty file]" },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isZip) {
                    Button(onClick = { terminalViewModel.unzipRemoteArchiveInExplorer(entry) }) {
                        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unzip Here")
                    }
                } else {
                    Button(
                        onClick = { terminalViewModel.runOrHostFile(entry) },
                        modifier = Modifier.testTag("preview_run_host_btn")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run / Host")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = terminalViewModel::dismissFilePreview) {
                    Text("Close")
                }
            }
        )
    }

    createFolderParentDir?.let { parentPath ->
        AlertDialog(
            onDismissRequest = { createFolderParentDir = null },
            title = { Text("Create Remote Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "In $parentPath",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isNotEmpty()) {
                            terminalViewModel.createRemoteFolder(name, parentPath)
                            createFolderParentDir = null
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { createFolderParentDir = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isNotEmpty() && clean != target.name) {
                            terminalViewModel.renameRemoteEntry(target, clean)
                        }
                        renameTarget = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${if (target.isDirectory) "Folder" else "File"}?") },
            text = {
                Text(
                    "Permanently delete \"${target.name}\" from remote server?" +
                        if (target.isDirectory) " All nested contents will be removed." else ""
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        terminalViewModel.deleteRemoteEntry(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    permissionsTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { permissionsTarget = null },
            title = { Text(target.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttrRow("Path", target.fullPath)
                    AttrRow("Type", if (target.isDirectory) "Directory" else if (target.isSymlink) "Symlink" else "File")
                    AttrRow("Permissions", "${target.permissionsString} (${target.octalPermissions})")
                    AttrRow("Owner UID / GID", "${target.uid} : ${target.gid}")
                    if (!target.isDirectory) {
                        AttrRow("Size", Validators.formatBytes(target.sizeBytes))
                    }
                    AttrRow("Modified", Validators.formatModifiedEpochSeconds(target.modifiedEpochSeconds))
                }
            },
            confirmButton = {
                if (!target.isDirectory) {
                    Button(
                        onClick = {
                            val file = target
                            permissionsTarget = null
                            terminalViewModel.downloadRemoteFile(file)
                        }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download")
                    }
                } else {
                    TextButton(onClick = { permissionsTarget = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (!target.isDirectory) {
                    OutlinedButton(onClick = { permissionsTarget = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onSelectPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(path) {
        path.trim('/').takeIf { it.isNotEmpty() }?.split('/') ?: emptyList()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        TextButton(
            onClick = { onSelectPath("/") },
            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 40.dp)
        ) {
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily
                )
            )
        }
        var cumulative = ""
        segments.forEach { seg ->
            cumulative = "$cumulative/$seg"
            val targetPath = cumulative
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { onSelectPath(targetPath) },
                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
            ) {
                Text(
                    text = seg,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    )
                )
            }
        }
    }
}

@Composable
private fun SftpEntryRow(
    entry: RemoteFileEntry,
    onTap: () -> Unit,
    onRunOrHost: () -> Unit,
    onUnzipHere: () -> Unit,
    onDownload: () -> Unit,
    onUploadIntoFolder: () -> Unit,
    onCreateFolderInside: () -> Unit,
    onRename: () -> Unit,
    onViewPermissions: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isRunnable = !entry.isDirectory && TerminalViewModel.isRunnableScriptName(entry.name)
    val isZip = !entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Icon(
            imageVector = when {
                entry.isDirectory -> Icons.Filled.Folder
                entry.isSymlink -> Icons.Filled.Link
                isZip -> Icons.Filled.Archive
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = if (entry.isDirectory || isRunnable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(entry.permissionsString)
                    if (!entry.isDirectory) {
                        append("  •  ")
                        append(Validators.formatBytes(entry.sizeBytes))
                    }
                    val mod = Validators.formatModifiedEpochSeconds(entry.modifiedEpochSeconds)
                    if (mod.isNotEmpty()) {
                        append("  •  ")
                        append(mod)
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

        if (isRunnable) {
            FilledTonalButton(
                onClick = onRunOrHost,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(end = 4.dp)
                    .testTag("run_host_row_${entry.name}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Host", style = MaterialTheme.typography.labelMedium)
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Actions for ${entry.name}"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (entry.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Upload File Here") },
                        leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onUploadIntoFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Create Subfolder") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCreateFolderInside()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("View File") },
                        leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTap()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Run / Host (Auto-Deps)") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRunOrHost()
                        }
                    )
                    if (isZip) {
                        DropdownMenuItem(
                            text = { Text("Unzip Archive Here") },
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onUnzipHere()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )

                DropdownMenuItem(
                    text = { Text("View Permissions") },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onViewPermissions()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun AttrRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily
            )
        )
    }
}
