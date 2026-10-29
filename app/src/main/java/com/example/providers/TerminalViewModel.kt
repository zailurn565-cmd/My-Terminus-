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

data class SftpTransferState(
    val fileName: String,
    val isUpload: Boolean,
    val progress: Float
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
    val terminalEngine = TerminalBufferEngine(initialCols = 80, initialRows = 24)

    val terminalSnapshot: StateFlow<TerminalSnapshot> = terminalEngine.snapshot
    val connectionState: StateFlow<SshConnectionState> = sshService.state

    private val _hostProfile = MutableStateFlow<HostProfile?>(null)
    val hostProfile: StateFlow<HostProfile?> = _hostProfile.asStateFlow()

    private val _selectedTab = MutableStateFlow(initialTabIndex.coerceIn(0, 1))
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _ctrlSticky = MutableStateFlow(false)
    val ctrlSticky: StateFlow<Boolean> = _ctrlSticky.asStateFlow()

    private val _altSticky = MutableStateFlow(false)
    val altSticky: StateFlow<Boolean> = _altSticky.asStateFlow()

    private val _scrollbackOffset = MutableStateFlow(0)
    val scrollbackOffset: StateFlow<Int> = _scrollbackOffset.asStateFlow()

    private val _activeHostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val activeHostKeyPrompt: StateFlow<HostKeyPrompt?> = _activeHostKeyPrompt.asStateFlow()
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null

    private val _remotePath = MutableStateFlow("/")
    val remotePath: StateFlow<String> = _remotePath.asStateFlow()

    private val _remoteEntries = MutableStateFlow<List<RemoteFileEntry>>(emptyList())
    val remoteEntries: StateFlow<List<RemoteFileEntry>> = _remoteEntries.asStateFlow()

    private val _isSftpLoading = MutableStateFlow(false)
    val isSftpLoading: StateFlow<Boolean> = _isSftpLoading.asStateFlow()

    private val _sftpTransfer = MutableStateFlow<SftpTransferState?>(null)
    val sftpTransfer: StateFlow<SftpTransferState?> = _sftpTransfer.asStateFlow()

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

    fun selectTab(index: Int) {
        val clamped = index.coerceIn(0, 1)
        _selectedTab.value = clamped
        if (clamped == 1 && connectionState.value == SshConnectionState.CONNECTED && !sftpInitialized) {
            loadInitialSftpDirectory()
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
                if (_selectedTab.value == 1) {
                    loadInitialSftpDirectory()
                }
            } catch (_: CancellationException) {
                // User cancelled connection modal
            } catch (missing: MissingCredentialException) {
                _events.emit(TerminalUiEvent.RedirectMissingCredential(missing.hostId))
            } catch (rejected: HostKeyRejectedException) {
                _events.emit(TerminalUiEvent.ShowMessage(rejected.message ?: "Host key rejected"))
            } catch (e: Exception) {
                _events.emit(TerminalUiEvent.ShowMessage(e.message ?: "Connection failed"))
            }
        }
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
            val resolver = appContext.contentResolver
            var displayName = "uploaded_file"
            var sizeBytes = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
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

            _sftpTransfer.value = SftpTransferState(displayName, isUpload = true, progress = 0f)
            try {
                val stream = resolver.openInputStream(uri)
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
