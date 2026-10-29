package com.example.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.models.AuthType
import com.example.models.HostProfile
import com.example.providers.HostViewModel
import com.example.providers.SettingsViewModel
import com.example.ui.theme.HostColorSwatches
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.utils.Validators
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HostEditScreen(
    editHostId: String?,
    missingCredentialNotice: Boolean,
    hostViewModel: HostViewModel,
    settingsViewModel: SettingsViewModel,
    onSaved: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var loadedExisting by rememberSaveable { mutableStateOf(false) }
    var existingProfile by remember { mutableStateOf<HostProfile?>(null) }

    var label by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf(settings.defaultPort.toString()) }
    var username by rememberSaveable { mutableStateOf(settings.defaultUsername) }
    var authTypeName by rememberSaveable { mutableStateOf(AuthType.PASSWORD.name) }
    val authType = runCatching { AuthType.valueOf(authTypeName) }.getOrDefault(AuthType.PASSWORD)

    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    var privateKeyPem by rememberSaveable { mutableStateOf("") }
    var keyPassphrase by rememberSaveable { mutableStateOf("") }
    var showPassphrase by rememberSaveable { mutableStateOf(false) }

    var group by rememberSaveable { mutableStateOf("") }
    var selectedColorTag by rememberSaveable { mutableLongStateOf(HostColorSwatches.first()) }
    var initialCommand by rememberSaveable { mutableStateOf("") }
    var connectTimeoutText by rememberSaveable { mutableStateOf("15") }
    var keepaliveText by rememberSaveable { mutableStateOf("30") }

    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editHostId, settings.defaultPort, settings.defaultUsername) {
        if (!loadedExisting) {
            if (editHostId != null) {
                val found = hostViewModel.getHostById(editHostId)
                if (found != null) {
                    existingProfile = found
                    label = found.label
                    host = found.host
                    portText = found.port.toString()
                    username = found.username
                    authTypeName = found.authType.name
                    group = found.group ?: ""
                    selectedColorTag = found.colorTagValue ?: HostColorSwatches.first()
                    initialCommand = found.initialCommand ?: ""
                    connectTimeoutText = found.connectTimeoutSeconds.toString()
                    keepaliveText = found.keepaliveSeconds.toString()

                    val creds = hostViewModel.loadCredentials(found.id)
                    password = creds.password ?: ""
                    privateKeyPem = creds.privateKey ?: ""
                    keyPassphrase = creds.passphrase ?: ""
                }
            } else {
                portText = settings.defaultPort.toString()
                username = settings.defaultUsername
            }
            loadedExisting = true
        }
    }

    LaunchedEffect(missingCredentialNotice) {
        if (missingCredentialNotice) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.missing_credential_message)
            )
        }
    }

    val pemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.onSuccess { content ->
                    if (!content.isNullOrBlank()) {
                        privateKeyPem = content.trim()
                        snackbarHostState.showSnackbar("Private key imported from file")
                    } else {
                        snackbarHostState.showSnackbar("Selected file was empty")
                    }
                }.onFailure {
                    snackbarHostState.showSnackbar("Could not read key file")
                }
            }
        }
    }

    val saveProfileAction: () -> Unit = saveBlock@{
        val err = Validators.validateLabel(label)
            ?: Validators.validateHost(host)
            ?: Validators.validatePort(portText)
            ?: Validators.validateUsername(username)
            ?: Validators.validatePositiveSeconds(connectTimeoutText, "Connect timeout", 300)
            ?: Validators.validatePositiveSeconds(keepaliveText, "Keepalive interval", 600)
            ?: when (authType) {
                AuthType.PASSWORD -> if (password.isEmpty()) "Password is required" else null
                AuthType.PRIVATE_KEY -> Validators.validatePrivateKeyPem(privateKeyPem)
            }

        if (err != null) {
            validationError = err
            scope.launch { snackbarHostState.showSnackbar(err) }
            return@saveBlock
        }

        validationError = null
        val targetId = existingProfile?.id ?: editHostId ?: UUID.randomUUID().toString()
        val updated = HostProfile(
            id = targetId,
            label = label.trim(),
            host = host.trim(),
            port = portText.trim().toInt(),
            username = username.trim(),
            authType = authType,
            keyFingerprint = existingProfile?.keyFingerprint,
            group = group.trim().takeIf { it.isNotEmpty() },
            colorTagValue = selectedColorTag,
            initialCommand = initialCommand.trim().takeIf { it.isNotEmpty() },
            connectTimeoutSeconds = connectTimeoutText.trim().toInt(),
            keepaliveSeconds = keepaliveText.trim().toInt(),
            lastConnectedAt = existingProfile?.lastConnectedAt
        )

        scope.launch {
            hostViewModel.saveHost(
                profile = updated,
                password = if (authType == AuthType.PASSWORD) password else null,
                privateKeyPem = if (authType == AuthType.PRIVATE_KEY) privateKeyPem else null,
                passphrase = if (authType == AuthType.PRIVATE_KEY) keyPassphrase else null
            )
            onSaved()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editHostId == null) "New SSH Host" else "Edit SSH Host")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("edit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = saveProfileAction,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("save_host_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
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
                            text = "Connection Endpoint",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Label (e.g. Prod API Node)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_label_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = { host = it },
                                label = { Text("Hostname or IP") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("host_address_input")
                            )

                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it.filter(Char::isDigit) },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(0.3f)
                                    .testTag("host_port_input")
                            )
                        }

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_username_input")
                        )
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
                            text = "Encrypted Vault Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = authType == AuthType.PASSWORD,
                                onClick = { authTypeName = AuthType.PASSWORD.name },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {
                                    Icon(
                                        Icons.Filled.Password,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("auth_mode_password")
                            ) {
                                Text("Password")
                            }
                            SegmentedButton(
                                selected = authType == AuthType.PRIVATE_KEY,
                                onClick = { authTypeName = AuthType.PRIVATE_KEY.name },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = {
                                    Icon(
                                        Icons.Filled.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("auth_mode_private_key")
                            ) {
                                Text("Private Key")
                            }
                        }

                        if (authType == AuthType.PASSWORD) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("SSH Password") },
                                singleLine = true,
                                visualTransformation = if (showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) {
                                                Icons.Filled.VisibilityOff
                                            } else {
                                                Icons.Filled.Visibility
                                            },
                                            contentDescription = if (showPassword) {
                                                "Hide password"
                                            } else {
                                                "Show password"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("host_password_input")
                            )
                        } else {
                            OutlinedButton(
                                onClick = { pemPickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("import_pem_button")
                            ) {
                                Icon(Icons.Filled.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import PEM / OpenSSH Key File")
                            }

                            OutlinedTextField(
                                value = privateKeyPem,
                                onValueChange = { privateKeyPem = it },
                                label = { Text("PEM / OpenSSH Private Key (RSA, ECDSA, Ed25519)") },
                                textStyle = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                minLines = 5,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("host_private_key_input")
                            )

                            OutlinedTextField(
                                value = keyPassphrase,
                                onValueChange = { keyPassphrase = it },
                                label = { Text("Key Passphrase (optional)") },
                                singleLine = true,
                                visualTransformation = if (showPassphrase) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                        Icon(
                                            imageVector = if (showPassphrase) {
                                                Icons.Filled.VisibilityOff
                                            } else {
                                                Icons.Filled.Visibility
                                            },
                                            contentDescription = if (showPassphrase) {
                                                "Hide passphrase"
                                            } else {
                                                "Show passphrase"
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("host_passphrase_input")
                            )
                        }

                        Text(
                            text = "Secrets are encrypted via Android Keystore EncryptedSharedPreferences and never stored in plain database tables.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            text = "Organization & Session Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = group,
                            onValueChange = { group = it },
                            label = { Text("Group / Environment Tag (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_group_input")
                        )

                        Text(
                            text = "Color Tag",
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HostColorSwatches.forEach { swatchValue ->
                                val isSelected = selectedColorTag == swatchValue
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { selectedColorTag = swatchValue }
                                        .padding(6.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(swatchValue))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = CircleShape
                                            )
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected color tag",
                                                tint = Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = initialCommand,
                            onValueChange = { initialCommand = it },
                            label = { Text("Initial Command on Connect (optional)") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("host_initial_cmd_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = connectTimeoutText,
                                onValueChange = { connectTimeoutText = it.filter(Char::isDigit) },
                                label = { Text("Timeout (s)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("host_timeout_input")
                            )

                            OutlinedTextField(
                                value = keepaliveText,
                                onValueChange = { keepaliveText = it.filter(Char::isDigit) },
                                label = { Text("Keepalive (s)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("host_keepalive_input")
                            )
                        }
                    }
                }

                validationError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
