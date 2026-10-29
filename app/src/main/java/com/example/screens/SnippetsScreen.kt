package com.example.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.models.HostProfile
import com.example.models.Snippet
import com.example.providers.HostViewModel
import com.example.providers.SnippetViewModel
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsScreen(
    snippetViewModel: SnippetViewModel,
    hostViewModel: HostViewModel,
    onNavigateBack: () -> Unit
) {
    val snippets by snippetViewModel.snippets.collectAsStateWithLifecycle()
    val hosts by hostViewModel.allHosts.collectAsStateWithLifecycle()

    var editingSnippet by remember { mutableStateOf<Snippet?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val hostMap = remember(hosts) { hosts.associateBy { it.id } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Command Snippets") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("snippets_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingSnippet = null
                    showEditor = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Snippet") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_snippet_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (snippets.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No snippets saved yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create reusable shell commands (e.g. docker ps, systemctl status, journalctl -fu) to run with one tap from the terminal toolbar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 380.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                        .testTag("snippets_list")
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        SnippetCard(
                            snippet = snippet,
                            scopedHost = snippet.hostId?.let { hostMap[it] },
                            onEdit = {
                                editingSnippet = snippet
                                showEditor = true
                            },
                            onDelete = { snippetViewModel.deleteSnippet(snippet.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        SnippetEditorDialog(
            initial = editingSnippet,
            hosts = hosts,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                snippetViewModel.upsertSnippet(saved)
                showEditor = false
            }
        )
    }
}

@Composable
private fun SnippetCard(
    snippet: Snippet,
    scopedHost: HostProfile?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("snippet_card_${snippet.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snippet.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = scopedHost?.let { "Host: ${it.label}" } ?: "Global (All Hosts)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit snippet ${snippet.label}"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete snippet ${snippet.label}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = snippet.command,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetEditorDialog(
    initial: Snippet?,
    hosts: List<HostProfile>,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit
) {
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var command by remember(initial) { mutableStateOf(initial?.command ?: "") }
    var selectedHostId by remember(initial) { mutableStateOf(initial?.hostId) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedHostLabel = remember(selectedHostId, hosts) {
        if (selectedHostId == null) {
            "Global (All Hosts)"
        } else {
            hosts.firstOrNull { it.id == selectedHostId }?.label ?: "Global (All Hosts)"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Snippet" else "Edit Snippet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Tail Nginx Logs)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snippet_label_input")
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Shell Command") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snippet_command_input")
                )

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedHostLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Scope") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Global (All Hosts)") },
                            onClick = {
                                selectedHostId = null
                                dropdownExpanded = false
                            }
                        )
                        hosts.forEach { host ->
                            DropdownMenuItem(
                                text = { Text("${host.label} (${host.connectionSummary})") },
                                onClick = {
                                    selectedHostId = host.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank() && command.isNotBlank()) {
                        onSave(
                            Snippet(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                label = label.trim(),
                                command = command.trimEnd('\r', '\n'),
                                hostId = selectedHostId
                            )
                        )
                    }
                },
                enabled = label.isNotBlank() && command.isNotBlank(),
                modifier = Modifier.testTag("save_snippet_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
