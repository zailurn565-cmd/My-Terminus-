package com.example.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.models.HostProfile
import com.example.providers.HostViewModel
import com.example.widgets.HostTile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    hostViewModel: HostViewModel,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
    onConnectHost: (hostId: String, initialTab: Int) -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val allHosts by hostViewModel.allHosts.collectAsStateWithLifecycle()
    val filteredHosts by hostViewModel.filteredHosts.collectAsStateWithLifecycle()
    val searchQuery by hostViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGroup by hostViewModel.selectedGroup.collectAsStateWithLifecycle()
    val groups by hostViewModel.availableGroups.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val handleDeleteWithUndo: (HostProfile) -> Unit = { profile ->
        hostViewModel.deleteHost(profile) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Deleted \"${profile.label}\"",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    hostViewModel.undoDelete()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "${allHosts.size} saved ${if (allHosts.size == 1) "host" else "hosts"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSnippets,
                        modifier = Modifier.testTag("nav_snippets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Code,
                            contentDescription = stringResource(R.string.action_snippets)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("nav_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddHost,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.action_add_host)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_host_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            val contentMaxWidth = if (maxWidth >= 720.dp) 760.dp else maxWidth

            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
            ) {
                if (allHosts.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = hostViewModel::setSearchQuery,
                        placeholder = {
                            Text(stringResource(R.string.search_hosts_placeholder))
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { hostViewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("host_search_input")
                    )

                    if (groups.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 10.dp)
                        ) {
                            FilterChip(
                                selected = selectedGroup == null,
                                onClick = { hostViewModel.setSelectedGroup(null) },
                                label = { Text("All Groups") },
                                modifier = Modifier.testTag("group_chip_all")
                            )
                            groups.forEach { grp ->
                                FilterChip(
                                    selected = selectedGroup.equals(grp, ignoreCase = true),
                                    onClick = {
                                        hostViewModel.setSelectedGroup(
                                            if (selectedGroup.equals(grp, ignoreCase = true)) null else grp
                                        )
                                    },
                                    label = { Text(grp) },
                                    modifier = Modifier.testTag("group_chip_$grp")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                when {
                    allHosts.isEmpty() -> {
                        EmptyHostsState(
                            onAddHost = onAddHost,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
                    filteredHosts.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hosts match \"$searchQuery\".",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("hosts_lazy_column")
                        ) {
                            items(
                                items = filteredHosts,
                                key = { it.id }
                            ) { host ->
                                HostTile(
                                    profile = host,
                                    onConnectTerminal = { onConnectHost(host.id, 0) },
                                    onConnectSftp = { onConnectHost(host.id, 1) },
                                    onEdit = { onEditHost(host.id) },
                                    onDuplicate = {
                                        hostViewModel.duplicateHost(host) { copy ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Duplicated as \"${copy.label}\""
                                                )
                                            }
                                        }
                                    },
                                    onSwipeDelete = { handleDeleteWithUndo(host) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHostsState(
    onAddHost: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 20.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_forge_empty_1793261664486),
                contentDescription = stringResource(R.string.cd_empty_illustration),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.empty_hosts_message),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("empty_hosts_title")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.empty_hosts_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 400.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddHost,
            modifier = Modifier.testTag("empty_state_forge_btn")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Forge Connection")
        }
    }
}
