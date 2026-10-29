package com.example.providers

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.models.HostProfile
import com.example.services.CredentialStore
import com.example.services.HostKeyPrompt
import com.example.services.HostKeyRejectedException
import com.example.services.HostRepository
import com.example.services.MissingCredentialException
import com.example.services.RemoteFileEntry
import com.example.services.SettingsRepository
import com.example.services.SftpService
import com.example.services.SshConnectionState
import com.example.services.SshService
import com.example.services.TerminalBufferEngine
import com.example.services.TerminalSnapshot
import com.example.services.TmuxSessionInfo
import com.example.services.VpsAutomationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class VpsWorkflowStage {
    HUB_HOME,
    UPLOAD_FOLDER_MODE,
    UPLOAD_PICK_EXISTING_FOLDER,
    UPLOAD_CREATE_NEW_FOLDER,
    UPLOAD_TMUX_AND_UNZIP,
    OPEN_TERMINAL_CHOICE,
    TMUX_SESSION_LIST,
    ACTIVE_TERMINAL,
    FILE_EXPLORER
}

data class SftpTransferState(
    val fileName: String,
    val isUpload: Boolean,
    val progress: Float
)

data class ZipDeployState(
    val targetFolder: String = "/root",
    val uploadedZipPath: String? = null,
    val uploadedZipName: String = "",
    val suggestedSessionName: String = "vps-app",
    val isBusy: Boolean = false,
    val statusText: String = ""
)

data class FilePreviewState(
    val entry: RemoteFileEntry,
    val isLoading: Boolean,
    val content: String = ""
)

sealed interface TerminalUiEvent {
    data class ShowMessage(val message: String) : TerminalUiEvent
    data class RedirectMissingCredential(val hostId: String) : TerminalUiEvent
    data object HapticConnected : TerminalUiEvent
    data object HapticDisconnected : TerminalUiEvent
}

class TerminalViewModel(
    private val hostId: String,
    initialTabIndex: Int,
    private val appContext: Context,
    private val hostRepository: HostRepository,
    private val credentialStore: CredentialStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val sshService = SshService()
    private val sftpService = SftpService(sshService)
    private val vpsAutomation = VpsAutomationService(appContext, sshService, sftpService)
    val terminalEngine = TerminalBufferEngine(initialCols = 80, initialRows = 24)

    val terminalSnapshot: StateFlow<TerminalSnapshot> = terminalEngine.snapshot
    val connectionState: StateFlow<SshConnectionState> = sshService.state

    private val _hostProfile = MutableStateFlow<HostProfile?>(null)
    val hostProfile: StateFlow<HostProfile?> = _hostProfile.asStateFlow()

    private val _workflowStage = MutableStateFlow(
        if (initialTabIndex == 1) VpsWorkflowStage.FILE_EXPLORER else VpsWorkflowStage.HUB_HOME
    )
    val workflowStage: StateFlow<VpsWorkflowStage> = _workflowStage.asStateFlow()

    private val _ctrlSticky = MutableStateFlow(false)
    val ctrlSticky: StateFlow<Boolean> = _ctrlSticky.asStateFlow()

    private val _altSticky = MutableStateFlow(false)
    val altSticky: StateFlow<Boolean> = _altSticky.asStateFlow()

    private val _scrollbackOffset = MutableStateFlow(0)
    val scrollbackOffset: StateFlow<Int> = _scrollbackOffset.asStateFlow()

    private val _activeHostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val activeHostKeyPrompt: StateFlow<HostKeyPrompt?> = _activeHostKeyPrompt.asStateFlow()
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null

    private val _homeDirectory = MutableStateFlow("/root")
    val homeDirectory: StateFlow<String> = _homeDirectory.asStateFlow()

    private val _activeProjectDirectory = MutableStateFlow("/root")
    val activeProjectDirectory: StateFlow<String> = _activeProjectDirectory.asStateFlow()

    private val _activeTmuxSessionName = MutableStateFlow<String?>(null)
    val activeTmuxSessionName: StateFlow<String?> = _activeTmuxSessionName.asStateFlow()

    private val _remotePath = MutableStateFlow("/")
    val remotePath: StateFlow<String> = _remotePath.asStateFlow()

    private val _remoteEntries = MutableStateFlow<List<RemoteFileEntry>>(emptyList())
    val remoteEntries: StateFlow<List<RemoteFileEntry>> = _remoteEntries.asStateFlow()

    private val _isSftpLoading = MutableStateFlow(false)
    val isSftpLoading: StateFlow<Boolean> = _isSftpLoading.asStateFlow()

    private val _sftpTransfer = MutableStateFlow<SftpTransferState?>(null)
    val sftpTransfer: StateFlow<SftpTransferState?> = _sftpTransfer.asStateFlow()

    private val _zipDeployState = MutableStateFlow(ZipDeployState())
    val zipDeployState: StateFlow<ZipDeployState> = _zipDeployState.asStateFlow()

    private val _tmuxSessions = MutableStateFlow<List<TmuxSessionInfo>>(emptyList())
    val tmuxSessions: StateFlow<List<TmuxSessionInfo>> = _tmuxSessions.asStateFlow()

    private val _isTmuxLoading = MutableStateFlow(false)
    val isTmuxLoading: StateFlow<Boolean> = _isTmuxLoading.asStateFlow()

    private val _filePreview = MutableStateFlow<FilePreviewState?>(null)
    val filePreview: StateFlow<FilePreviewState?> = _filePreview.asStateFlow()

    private val _runnableProjectFiles = MutableStateFlow<List<RemoteFileEntry>>(emptyList())
    val runnableProjectFiles: StateFlow<List<RemoteFileEntry>> = _runnableProjectFiles.asStateFlow()

    private val _events = MutableSharedFlow<TerminalUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TerminalUiEvent> = _events.asSharedFlow()

    private var connectJob: Job? = null
    private var userInitiatedDisconnect = false
    private var sftpInitialized = false
    private var currentCols = 80
    private var currentRows = 24

    init {
        viewModelScope.launch {
            sshService.output.collect { bytes ->
                terminalEngine.feedBytes(bytes)
            }
        }

        viewModelScope.launch {
            var previous = SshConnectionState.DISCONNECTED
            sshService.state.collect { next ->
                if (previous == SshConnectionState.CONNECTED &&
                    next == SshConnectionState.DISCONNECTED
                ) {
                    _events.tryEmit(TerminalUiEvent.HapticDisconnected)
                    terminalEngine.feedLocalText("\r\n\u001b[38;5;208m[ForgeShell: Session disconnected]\u001b[0m\r\n")
                    val autoReconnect = settingsRepository.settingsFlow.first().autoReconnect
                    if (autoReconnect && !userInitiatedDisconnect) {
                        delay(1500L)
                        if (!userInitiatedDisconnect &&
                            sshService.state.value == SshConnectionState.DISCONNECTED
                        ) {
                            connect()
                        }
                    }
                }
                previous = next
            }
        }

        connect()
    }

    fun openHubHome() {
        _workflowStage.value = VpsWorkflowStage.HUB_HOME
    }

    fun selectUploadFlow() {
        _workflowStage.value = VpsWorkflowStage.UPLOAD_FOLDER_MODE
    }

    fun selectExistingFolderUploadMode() {
        _workflowStage.value = VpsWorkflowStage.UPLOAD_PICK_EXISTING_FOLDER
        if (!sftpInitialized) {
            loadInitialSftpDirectory()
        } else {
            refreshSftp()
        }
    }

    fun selectNewFolderUploadMode() {
        _workflowStage.value = VpsWorkflowStage.UPLOAD_CREATE_NEW_FOLDER
    }

    fun selectOpenTerminalFlow() {
        _workflowStage.value = VpsWorkflowStage.OPEN_TERMINAL_CHOICE
    }

    fun openTmuxSessionsPicker() {
        _workflowStage.value = VpsWorkflowStage.TMUX_SESSION_LIST
        refreshTmuxSessions()
    }

    fun openFileExplorerStage() {
        _workflowStage.value = VpsWorkflowStage.FILE_EXPLORER
        if (!sftpInitialized) {
            loadInitialSftpDirectory()
        } else {
            refreshSftp()
        }
    }

    fun openRawTerminalStage() {
        _workflowStage.value = VpsWorkflowStage.ACTIVE_TERMINAL
        refreshRunnableFilesInActiveDir()
    }

    /**
     * Handles Back button navigation within the VPS workflow before exiting the SSH session.
     * Returns true if consumed internally, false if the screen should disconnect and pop.
     */
    fun handleWorkflowBack(): Boolean {
        return when (_workflowStage.value) {
            VpsWorkflowStage.HUB_HOME -> false
            VpsWorkflowStage.UPLOAD_FOLDER_MODE,
            VpsWorkflowStage.OPEN_TERMINAL_CHOICE,
            VpsWorkflowStage.ACTIVE_TERMINAL,
            VpsWorkflowStage.FILE_EXPLORER -> {
                _workflowStage.value = VpsWorkflowStage.HUB_HOME
                true
            }
            VpsWorkflowStage.UPLOAD_PICK_EXISTING_FOLDER,
            VpsWorkflowStage.UPLOAD_CREATE_NEW_FOLDER -> {
                _workflowStage.value = VpsWorkflowStage.UPLOAD_FOLDER_MODE
                true
            }
            VpsWorkflowStage.UPLOAD_TMUX_AND_UNZIP -> {
                if (!_zipDeployState.value.isBusy) {
                    _workflowStage.value = VpsWorkflowStage.UPLOAD_FOLDER_MODE
                }
                true
            }
            VpsWorkflowStage.TMUX_SESSION_LIST -> {
                _workflowStage.value = VpsWorkflowStage.OPEN_TERMINAL_CHOICE
                true
            }
        }
    }

    fun connect() {
        connectJob?.cancel()
        userInitiatedDisconnect = false
        sftpInitialized = false
        connectJob = viewModelScope.launch {
            val profile = hostRepository.getById(hostId)
            if (profile == null) {
                _events.emit(TerminalUiEvent.ShowMessage("Host profile not found"))
                return@launch
            }
            _hostProfile.value = profile
            try {
                sshService.connect(
                    profile = profile,
                    credentialStore = credentialStore,
                    hostRepository = hostRepository,
                    initialCols = currentCols,
                    initialRows = currentRows,
                    onVerifyHostKey = { prompt ->
                        val deferred = CompletableDeferred<Boolean>()
                        hostKeyDecision = deferred
                        _activeHostKeyPrompt.value = prompt
                        try {
                            deferred.await()
                        } finally {
                            _activeHostKeyPrompt.value = null
                            hostKeyDecision = null
                        }
                    }
                )
                _events.emit(TerminalUiEvent.HapticConnected)

                // Bootstrap home path and ~/.forgeshell_auto.sh (--break-system-packages helper)
                launch {
                    runCatching {
                        val home = sftpService.resolveHomeDirectory()
                        _homeDirectory.value = home
                        _activeProjectDirectory.value = home
                        if (!sftpInitialized) {
                            _remotePath.value = home
                        }
                        vpsAutomation.ensureRunnerInstalled(home)
                        sshService.send("[ -f ~/.forgeshell_auto.sh ] && . ~/.forgeshell_auto.sh\r")
                    }
                }

                if (_workflowStage.value == VpsWorkflowStage.FILE_EXPLORER) {
                    loadInitialSftpDirectory()
                }
            } catch (_: CancellationException) {
                // Cancelled by user
            } catch (missing: MissingCredentialException) {
                _events.emit(TerminalUiEvent.RedirectMissingCredential(missing.hostId))
            } catch (rejected: HostKeyRejectedException) {
                _events.emit(TerminalUiEvent.ShowMessage(rejected.message ?: "Host key rejected"))
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage(e.message ?: "Connection failed"))
            }
        }
    }

    fun createNewProjectFolderAndUploadZip(
        parentDirectory: String,
        newFolderName: String,
        zipUri: Uri
    ) {
        val cleanName = newFolderName.trim().trim('/')
        if (cleanName.isEmpty()) {
            _events.tryEmit(TerminalUiEvent.ShowMessage("Please enter a folder name"))
            return
        }
        val fullTargetDir = SftpService.joinRemotePath(parentDirectory, cleanName)
        uploadProjectZipToFolder(targetFolder = fullTargetDir, zipUri = zipUri, createFolderFirst = true)
    }

    fun uploadProjectZipToFolder(
        targetFolder: String,
        zipUri: Uri,
        createFolderFirst: Boolean = false
    ) {
        viewModelScope.launch {
            val (displayName, sizeBytes) = resolveUriMetadata(zipUri, fallbackName = "project.zip")
            val suggestedSession = VpsAutomationService.sanitizeSessionName(
                targetFolder.substringAfterLast('/').ifBlank { displayName }
            )
            _zipDeployState.value = ZipDeployState(
                targetFolder = targetFolder,
                uploadedZipPath = null,
                uploadedZipName = displayName,
                suggestedSessionName = suggestedSession,
                isBusy = true,
                statusText = "Uploading $displayName…"
            )
            _workflowStage.value = VpsWorkflowStage.UPLOAD_TMUX_AND_UNZIP
            _sftpTransfer.value = SftpTransferState(displayName, isUpload = true, progress = 0f)

            try {
                if (createFolderFirst) {
                    sftpService.ensureDirectoryPath(targetFolder)
                }
                val stream = appContext.contentResolver.openInputStream(zipUri)
                    ?: throw IllegalStateException("Cannot read selected .zip file")
                val remoteZipPath = sftpService.uploadStream(
                    inputStream = stream,
                    fileName = displayName,
                    totalBytes = sizeBytes,
                    remoteDirectory = targetFolder
                ) { pct ->
                    _sftpTransfer.value = SftpTransferState(displayName, isUpload = true, progress = pct)
                }
                _zipDeployState.value = _zipDeployState.value.copy(
                    uploadedZipPath = remoteZipPath,
                    isBusy = false,
                    statusText = "Uploaded $displayName. Enter new tmux session name to auto-unzip."
                )
            } catch (e: Exception) {
                _zipDeployState.value = _zipDeployState.value.copy(
                    isBusy = false,
                    statusText = "Upload error: ${e.message ?: "Failed"}"
                )
                _events.emit(TerminalUiEvent.ShowMessage("Upload failed: ${e.message ?: "Error"}"))
            } finally {
                _sftpTransfer.value = null
            }
        }
    }

    fun completeTmuxCreationAndAutoUnzip(requestedSessionName: String) {
        val currentDeploy = _zipDeployState.value
        val zipPath = currentDeploy.uploadedZipPath ?: return
        viewModelScope.launch {
            _zipDeployState.value = currentDeploy.copy(
                isBusy = true,
                statusText = "Creating tmux session & extracting archive…"
            )
            try {
                val (sessionName, resolvedDir) = vpsAutomation.unzipArchiveAndPrepareTmux(
                    targetFolder = currentDeploy.targetFolder,
                    uploadedZipRemotePath = zipPath,
                    sessionName = requestedSessionName
                ) { stepMessage ->
                    _zipDeployState.value = _zipDeployState.value.copy(statusText = stepMessage)
                }
                _activeProjectDirectory.value = resolvedDir
                _remotePath.value = resolvedDir
                _activeTmuxSessionName.value = sessionName
                _workflowStage.value = VpsWorkflowStage.ACTIVE_TERMINAL
                switchOrAttachInteractiveTmux(sessionName, resolvedDir)
                refreshRunnableFilesInDir(resolvedDir)
                _events.emit(
                    TerminalUiEvent.ShowMessage("Unzipped & attached to tmux session \"$sessionName\"")
                )
            } catch (e: Exception) {
                _zipDeployState.value = _zipDeployState.value.copy(
                    isBusy = false,
                    statusText = "Setup failed: ${e.message ?: "Unknown error"}"
                )
                _events.emit(TerminalUiEvent.ShowMessage("Auto-unzip error: ${e.message ?: "Error"}"))
            }
        }
    }

    fun refreshTmuxSessions() {
        viewModelScope.launch {
            _isTmuxLoading.value = true
            try {
                _tmuxSessions.value = vpsAutomation.listTmuxSessions()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Could not list tmux sessions: ${e.message}"))
            } finally {
                _isTmuxLoading.value = false
            }
        }
    }

    fun attachToTmuxSession(sessionName: String, workingDir: String? = null) {
        viewModelScope.launch {
            val safeName = VpsAutomationService.sanitizeSessionName(sessionName)
            try {
                val dir = workingDir?.takeIf { it.isNotBlank() } ?: _homeDirectory.value
                vpsAutomation.createOrEnsureTmuxSession(safeName, dir)
                _activeTmuxSessionName.value = safeName
                _activeProjectDirectory.value = dir
                _workflowStage.value = VpsWorkflowStage.ACTIVE_TERMINAL
                switchOrAttachInteractiveTmux(safeName, dir)
                refreshRunnableFilesInDir(dir)
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Failed to attach tmux: ${e.message}"))
            }
        }
    }

    fun killTmuxSession(sessionName: String) {
        viewModelScope.launch {
            try {
                vpsAutomation.killTmuxSession(sessionName)
                if (_activeTmuxSessionName.value == sessionName) {
                    _activeTmuxSessionName.value = null
                }
                refreshTmuxSessions()
                _events.emit(TerminalUiEvent.ShowMessage("Stopped tmux session \"$sessionName\""))
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Could not stop session: ${e.message}"))
            }
        }
    }

    /**
     * Runs or hosts any file on the VPS: ensures ~/.forgeshell_auto.sh is installed,
     * auto-installs requirements (--break-system-packages for pip / npm install for Node),
     * and launches inside a persistent tmux session while switching to the live Terminal.
     */
    fun runOrHostFile(entry: RemoteFileEntry, customSessionName: String? = null) {
        if (entry.isDirectory) return
        viewModelScope.launch {
            try {
                val parentDir = SftpService.parentRemotePath(entry.fullPath)
                val defaultSession = customSessionName?.takeIf { it.isNotBlank() }
                    ?: _activeTmuxSessionName.value
                    ?: VpsAutomationService.sanitizeSessionName(
                        parentDir.substringAfterLast('/').ifBlank { entry.name.substringBeforeLast('.') }
                    )
                val safeSession = vpsAutomation.createOrEnsureTmuxSession(defaultSession, parentDir)
                _activeTmuxSessionName.value = safeSession
                _activeProjectDirectory.value = parentDir
                _filePreview.value = null
                _workflowStage.value = VpsWorkflowStage.ACTIVE_TERMINAL

                switchOrAttachInteractiveTmux(safeSession, parentDir)
                delay(250L)

                val runCmd = "[ -f ~/.forgeshell_auto.sh ] && . ~/.forgeshell_auto.sh; cd ${
                    VpsAutomationService.shellQuote(parentDir)
                } && forgerun ${VpsAutomationService.shellQuote(entry.fullPath)}"

                sshService.execCommand(
                    command = "tmux send-keys -t ${VpsAutomationService.shellQuote(safeSession)} ${
                        VpsAutomationService.shellQuote(runCmd)
                    } C-m",
                    timeoutSeconds = 20
                )
                refreshRunnableFilesInDir(parentDir)
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Run/Host failed: ${e.message ?: "Error"}"))
            }
        }
    }

    fun unzipRemoteArchiveInExplorer(entry: RemoteFileEntry) {
        viewModelScope.launch {
            _isSftpLoading.value = true
            try {
                vpsAutomation.unzipFileInPlace(entry.fullPath)
                _filePreview.value = null
                _events.emit(TerminalUiEvent.ShowMessage("Unzipped ${entry.name}"))
                refreshSftp()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Unzip failed: ${e.message ?: "Error"}"))
            } finally {
                _isSftpLoading.value = false
            }
        }
    }

    fun openFilePreview(entry: RemoteFileEntry) {
        if (entry.isDirectory) return
        _filePreview.value = FilePreviewState(entry = entry, isLoading = true)
        viewModelScope.launch {
            try {
                val text = sftpService.readFilePreview(entry.fullPath)
                _filePreview.value = FilePreviewState(
                    entry = entry,
                    isLoading = false,
                    content = text
                )
            } catch (e: Exception) {
                _filePreview.value = FilePreviewState(
                    entry = entry,
                    isLoading = false,
                    content = "Unable to preview file: ${e.message}"
                )
            }
        }
    }

    fun dismissFilePreview() {
        _filePreview.value = null
    }

    fun refreshRunnableFilesInActiveDir() {
        refreshRunnableFilesInDir(_activeProjectDirectory.value)
    }

    private fun refreshRunnableFilesInDir(dirPath: String) {
        viewModelScope.launch {
            runCatching {
                val files = sftpService.listDirectory(dirPath)
                    .filter { !it.isDirectory && isRunnableScriptName(it.name) }
                _runnableProjectFiles.value = files
            }
        }
    }

    private suspend fun switchOrAttachInteractiveTmux(sessionName: String, workingDir: String) {
        val safe = VpsAutomationService.sanitizeSessionName(sessionName)
        val clientsCheck = sshService.execCommand(
            command = "tmux list-clients -F '#{client_name}' 2>/dev/null || true",
            timeoutSeconds = 10
        )
        val hasAttachedClient = clientsCheck.stdout.lines().any { it.trim().isNotEmpty() }
        if (hasAttachedClient) {
            sshService.execCommand(
                command = "tmux switch-client -t ${VpsAutomationService.shellQuote(safe)}",
                timeoutSeconds = 10
            )
        } else {
            sshService.send(VpsAutomationService.buildAttachTmuxShellLine(safe, workingDir))
        }
    }

    private fun resolveUriMetadata(uri: Uri, fallbackName: String): Pair<String, Long> {
        var displayName = fallbackName
        var sizeBytes = 0L
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }
        return displayName to sizeBytes
    }

    fun cancelConnecting() {
        userInitiatedDisconnect = true
        hostKeyDecision?.complete(false)
        connectJob?.cancel()
        viewModelScope.launch {
            sshService.disconnect()
        }
    }

    fun resolveHostKeyPrompt(trusted: Boolean) {
        hostKeyDecision?.complete(trusted)
    }

    fun disconnectUserSession() {
        userInitiatedDisconnect = true
        connectJob?.cancel()
        viewModelScope.launch {
            sshService.disconnect()
        }
    }

    fun toggleCtrl() {
        _ctrlSticky.value = !_ctrlSticky.value
    }

    fun toggleAlt() {
        _altSticky.value = !_altSticky.value
    }

    fun sendTypedChar(char: Char) {
        _scrollbackOffset.value = 0
        var payload = char.toString()
        if (_ctrlSticky.value) {
            _ctrlSticky.value = false
            val upper = char.uppercaseChar().code
            val ctrlByte = when {
                upper in 64..95 -> (upper - 64).toByte()
                char == ' ' -> 0.toByte()
                char == '/' -> 31.toByte()
                else -> null
            }
            if (ctrlByte != null) {
                if (_altSticky.value) {
                    _altSticky.value = false
                    sshService.sendBytes(byteArrayOf(0x1B, ctrlByte))
                } else {
                    sshService.sendBytes(byteArrayOf(ctrlByte))
                }
                return
            }
        }
        if (_altSticky.value) {
            _altSticky.value = false
            payload = "\u001b$payload"
        }
        sshService.send(payload)
    }

    fun sendRawSequence(sequence: String) {
        _scrollbackOffset.value = 0
        sshService.send(sequence)
    }

    fun executeSnippetCommand(command: String) {
        _scrollbackOffset.value = 0
        sshService.send("${command.trimEnd('\r', '\n')}\r")
    }

    fun pasteText(text: String) {
        if (text.isEmpty()) return
        _scrollbackOffset.value = 0
        sshService.send(text)
    }

    fun onTerminalViewportResized(cols: Int, rows: Int) {
        currentCols = cols.coerceIn(15, 300)
        currentRows = rows.coerceIn(5, 150)
        val changed = terminalEngine.resize(currentCols, currentRows)
        if (changed) {
            sshService.resize(currentCols, currentRows)
        }
    }

    fun scrollHistoryBy(deltaLines: Int) {
        val maxScroll = terminalSnapshot.value.scrollbackCount
        _scrollbackOffset.value = (_scrollbackOffset.value + deltaLines).coerceIn(0, maxScroll)
    }

    fun jumpToLiveBottom() {
        _scrollbackOffset.value = 0
    }

    fun clearTerminalBuffer() {
        _scrollbackOffset.value = 0
        terminalEngine.clearAll()
        if (connectionState.value == SshConnectionState.CONNECTED) {
            sshService.send("\u000c")
        }
    }

    private fun loadInitialSftpDirectory() {
        viewModelScope.launch {
            _isSftpLoading.value = true
            try {
                val home = sftpService.resolveHomeDirectory()
                _homeDirectory.value = home
                _remotePath.value = home
                _remoteEntries.value = sftpService.listDirectory(home)
                sftpInitialized = true
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("SFTP error: ${e.message ?: "Unable to list directory"}"))
            } finally {
                _isSftpLoading.value = false
            }
        }
    }

    fun navigateSftpTo(path: String) {
        viewModelScope.launch {
            _isSftpLoading.value = true
            try {
                val normalized = SftpService.normalizeRemotePath(path)
                val listed = sftpService.listDirectory(normalized)
                _remotePath.value = normalized
                _activeProjectDirectory.value = normalized
                _remoteEntries.value = listed
                sftpInitialized = true
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Failed to open $path: ${e.message ?: "Access denied"}"))
            } finally {
                _isSftpLoading.value = false
            }
        }
    }

    fun navigateSftpUp() {
        val current = _remotePath.value
        if (current == "/") return
        navigateSftpTo(SftpService.parentRemotePath(current))
    }

    fun refreshSftp() {
        navigateSftpTo(_remotePath.value)
    }

    fun downloadRemoteFile(entry: RemoteFileEntry) {
        if (entry.isDirectory) return
        viewModelScope.launch {
            val docsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(appContext.filesDir, "Documents")
            _sftpTransfer.value = SftpTransferState(entry.name, isUpload = false, progress = 0f)
            try {
                val savedFile = sftpService.downloadFile(entry, docsDir) { pct ->
                    _sftpTransfer.value = SftpTransferState(entry.name, isUpload = false, progress = pct)
                }
                _events.emit(
                    TerminalUiEvent.ShowMessage("Saved to app documents: ${savedFile.name}")
                )
            } catch (e: Exception) {
                _events.emit(
                    TerminalUiEvent.ShowMessage("Download failed: ${e.message ?: "I/O error"}")
                )
            } finally {
                _sftpTransfer.value = null
            }
        }
    }

    fun uploadFromUri(uri: Uri, targetDirectory: String = _remotePath.value) {
        viewModelScope.launch {
            val (displayName, sizeBytes) = resolveUriMetadata(uri, "uploaded_file")
            _sftpTransfer.value = SftpTransferState(displayName, isUpload = true, progress = 0f)
            try {
                val stream = appContext.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read selected file")
                sftpService.uploadStream(
                    inputStream = stream,
                    fileName = displayName,
                    totalBytes = sizeBytes,
                    remoteDirectory = targetDirectory
                ) { pct ->
                    _sftpTransfer.value = SftpTransferState(displayName, isUpload = true, progress = pct)
                }
                _events.emit(TerminalUiEvent.ShowMessage("Uploaded $displayName"))
                refreshSftp()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Upload failed: ${e.message ?: "I/O error"}"))
            } finally {
                _sftpTransfer.value = null
            }
        }
    }

    fun createRemoteFolder(folderName: String, parentDir: String = _remotePath.value) {
        viewModelScope.launch {
            try {
                sftpService.createFolder(parentDir, folderName)
                _events.emit(TerminalUiEvent.ShowMessage("Created folder $folderName"))
                refreshSftp()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Create folder failed: ${e.message ?: "Error"}"))
            }
        }
    }

    fun renameRemoteEntry(entry: RemoteFileEntry, newName: String) {
        viewModelScope.launch {
            try {
                sftpService.renameEntry(entry.fullPath, newName)
                _events.emit(TerminalUiEvent.ShowMessage("Renamed to $newName"))
                refreshSftp()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Rename failed: ${e.message ?: "Error"}"))
            }
        }
    }

    fun deleteRemoteEntry(entry: RemoteFileEntry) {
        viewModelScope.launch {
            try {
                sftpService.deleteEntry(entry)
                _filePreview.value = null
                _events.emit(TerminalUiEvent.ShowMessage("Deleted ${entry.name}"))
                refreshSftp()
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage("Delete failed: ${e.message ?: "Error"}"))
            }
        }
    }

    override fun onCleared() {
        userInitiatedDisconnect = true
        hostKeyDecision?.complete(false)
        connectJob?.cancel()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            sshService.disconnect()
        }
        super.onCleared()
    }

    companion object {
        fun isRunnableScriptName(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".py") ||
                lower.endsWith(".js") ||
                lower.endsWith(".mjs") ||
                lower.endsWith(".cjs") ||
                lower.endsWith(".sh")
        }

        fun factory(
            hostId: String,
            initialTabIndex: Int,
            container: ForgeContainer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TerminalViewModel(
                    hostId = hostId,
                    initialTabIndex = initialTabIndex,
                    appContext = container.appContext,
                    hostRepository = container.hostRepository,
                    credentialStore = container.credentialStore,
                    settingsRepository = container.settingsRepository
                ) as T
            }
        }
    }
}
