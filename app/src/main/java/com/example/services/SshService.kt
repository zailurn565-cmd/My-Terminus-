package com.example.services

import android.util.Base64
import com.example.models.AuthType
import com.example.models.HostProfile
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Security

enum class SshConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val algorithm: String,
    val presentedFingerprint: String,
    val expectedFingerprint: String?,
    val isMismatch: Boolean
)

class MissingCredentialException(val hostId: String) :
    Exception("Credentials missing for host")

class HostKeyRejectedException(val isMismatch: Boolean) :
    Exception(
        if (isMismatch) "Connection aborted: remote host key mismatch"
        else "Connection aborted: host key was not trusted"
    )

class SshConnectException(message: String) : Exception(message)

class SshService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sftpMutex = Mutex()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var shellChannel: ChannelShell? = null

    @Volatile
    private var sftpChannel: ChannelSftp? = null

    @Volatile
    private var shellOutput: OutputStream? = null

    private var readerJob: Job? = null

    private val _state = MutableStateFlow(SshConnectionState.DISCONNECTED)
    val state: StateFlow<SshConnectionState> = _state.asStateFlow()

    private val _output = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val output: SharedFlow<ByteArray> = _output.asSharedFlow()

    init {
        ensureBouncyCastleRegistered()
    }

    suspend fun connect(
        profile: HostProfile,
        credentialStore: CredentialStore,
        hostRepository: HostRepository,
        initialCols: Int = 80,
        initialRows: Int = 24,
        onVerifyHostKey: suspend (HostKeyPrompt) -> Boolean
    ) = withContext(Dispatchers.IO) {
        disconnectInternal(updateState = false)
        _state.value = SshConnectionState.CONNECTING

        val password: String?
        val privateKeyPem: String?
        val passphrase: String?

        when (profile.authType) {
            AuthType.PASSWORD -> {
                password = credentialStore.readPassword(profile.id)
                privateKeyPem = null
                passphrase = null
                if (password.isNullOrEmpty()) {
                    _state.value = SshConnectionState.DISCONNECTED
                    throw MissingCredentialException(profile.id)
                }
            }
            AuthType.PRIVATE_KEY -> {
                password = null
                privateKeyPem = credentialStore.readPrivateKey(profile.id)
                passphrase = credentialStore.readKeyPassphrase(profile.id)
                if (privateKeyPem.isNullOrBlank()) {
                    _state.value = SshConnectionState.DISCONNECTED
                    throw MissingCredentialException(profile.id)
                }
            }
        }

        val timeoutMs = (profile.connectTimeoutSeconds.coerceIn(1, 300)) * 1000
        var rejectedReason: HostKeyRejectedException? = null

        try {
            val jsch = JSch()

            if (profile.authType == AuthType.PRIVATE_KEY && privateKeyPem != null) {
                val keyBytes = privateKeyPem.trim().toByteArray(Charsets.UTF_8)
                val passBytes = passphrase?.toByteArray(Charsets.UTF_8)
                try {
                    jsch.addIdentity("forgeshell-key-${profile.id}", keyBytes, null, passBytes)
                } catch (_: JSchException) {
                    _state.value = SshConnectionState.ERROR
                    throw SshConnectException("Authentication failed: invalid private key or passphrase")
                } finally {
                    keyBytes.fill(0)
                    passBytes?.fill(0)
                }
            }

            jsch.hostKeyRepository = object : HostKeyRepository {
                override fun check(host: String, key: ByteArray): Int {
                    val fingerprint = computeSha256Fingerprint(key)
                    val algorithm = extractSshKeyAlgorithm(key)
                    return runBlocking {
                        val existing = hostRepository.getKnownHost(profile.host, profile.port)
                        val expectedFp = existing?.fingerprint ?: profile.keyFingerprint
                        if (expectedFp != null && expectedFp == fingerprint) {
                            if (existing == null) {
                                hostRepository.trustHostKey(
                                    host = profile.host,
                                    port = profile.port,
                                    algorithm = algorithm,
                                    fingerprint = fingerprint,
                                    hostProfileId = profile.id
                                )
                            }
                            HostKeyRepository.OK
                        } else {
                            val isMismatch = expectedFp != null && expectedFp != fingerprint
                            val approved = onVerifyHostKey(
                                HostKeyPrompt(
                                    host = profile.host,
                                    port = profile.port,
                                    algorithm = algorithm,
                                    presentedFingerprint = fingerprint,
                                    expectedFingerprint = expectedFp,
                                    isMismatch = isMismatch
                                )
                            )
                            if (approved) {
                                hostRepository.trustHostKey(
                                    host = profile.host,
                                    port = profile.port,
                                    algorithm = algorithm,
                                    fingerprint = fingerprint,
                                    hostProfileId = profile.id
                                )
                                HostKeyRepository.OK
                            } else {
                                rejectedReason = HostKeyRejectedException(isMismatch)
                                HostKeyRepository.CHANGED
                            }
                        }
                    }
                }

                override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
                override fun remove(host: String?, type: String?) = Unit
                override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
                override fun getKnownHostsRepositoryID(): String = "forgeshell_known_hosts"
                override fun getHostKey(): Array<HostKey> = emptyArray()
                override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
            }

            val sshSession = jsch.getSession(
                profile.username.trim(),
                profile.host.trim(),
                profile.port
            )
            this@SshService.session = sshSession

            sshSession.setConfig("StrictHostKeyChecking", "yes")
            if (profile.authType == AuthType.PASSWORD && password != null) {
                sshSession.setPassword(password)
                sshSession.setConfig("PreferredAuthentications", "password,keyboard-interactive")
                sshSession.userInfo = PasswordUserInfo(password)
            } else {
                sshSession.setConfig("PreferredAuthentications", "publickey")
            }

            if (profile.keepaliveSeconds > 0) {
                sshSession.setServerAliveInterval(profile.keepaliveSeconds * 1000)
                sshSession.serverAliveCountMax = 3
            }
            sshSession.timeout = timeoutMs

            sshSession.connect(timeoutMs)

            val shell = (sshSession.openChannel("shell") as ChannelShell).apply {
                setPty(true)
                setPtyType(
                    "xterm-256color",
                    initialCols.coerceAtLeast(20),
                    initialRows.coerceAtLeast(5),
                    initialCols.coerceAtLeast(20) * 8,
                    initialRows.coerceAtLeast(5) * 16
                )
                setEnv("TERM", "xterm-256color")
                setEnv("COLORTERM", "truecolor")
            }

            val inputStream = shell.inputStream
            val outStream = shell.outputStream
            shell.connect(timeoutMs)

            this@SshService.shellChannel = shell
            this@SshService.shellOutput = outStream
            _state.value = SshConnectionState.CONNECTED

            hostRepository.touchLastConnected(profile.id)

            startShellReader(inputStream)

            val initialCmd = profile.initialCommand?.trim()
            if (!initialCmd.isNullOrEmpty()) {
                serviceScope.launch {
                    delay(250L)
                    send("$initialCmd\r")
                }
            }
        } catch (cancel: CancellationException) {
            disconnectInternal(updateState = true)
            throw cancel
        } catch (rejected: HostKeyRejectedException) {
            disconnectInternal(updateState = true)
            throw rejected
        } catch (connectErr: SshConnectException) {
            disconnectInternal(updateState = false)
            _state.value = SshConnectionState.ERROR
            throw connectErr
        } catch (e: Exception) {
            rejectedReason?.let {
                disconnectInternal(updateState = true)
                throw it
            }
            disconnectInternal(updateState = false)
            _state.value = SshConnectionState.ERROR
            throw SshConnectException(mapSshError(e, profile.connectTimeoutSeconds))
        }
    }

    private fun startShellReader(inputStream: InputStream) {
        readerJob?.cancel()
        readerJob = serviceScope.launch {
            val buffer = ByteArray(8192)
            try {
                while (isActive) {
                    val readCount = inputStream.read(buffer)
                    if (readCount <= 0) break
                    val chunk = buffer.copyOfRange(0, readCount)
                    _output.emit(chunk)
                }
            } catch (_: Exception) {
                // Stream closed on disconnect or remote exit
            } finally {
                if (_state.value == SshConnectionState.CONNECTED) {
                    disconnectInternal(updateState = true)
                }
            }
        }
    }

    fun send(data: String) {
        sendBytes(data.toByteArray(Charsets.UTF_8))
    }

    fun sendBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        serviceScope.launch {
            try {
                val out = shellOutput ?: return@launch
                synchronized(out) {
                    out.write(bytes)
                    out.flush()
                }
            } catch (_: Exception) {
                disconnectInternal(updateState = true)
            }
        }
    }

    fun resize(cols: Int, rows: Int) {
        val safeCols = cols.coerceIn(15, 300)
        val safeRows = rows.coerceIn(5, 150)
        serviceScope.launch {
            runCatching {
                val ch = shellChannel
                if (ch != null && ch.isConnected) {
                    ch.setPtySize(safeCols, safeRows, safeCols * 8, safeRows * 16)
                }
            }
        }
    }

    suspend fun getOrOpenSftp(timeoutSeconds: Int = 15): ChannelSftp = sftpMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = sftpChannel
            if (existing != null && existing.isConnected) {
                return@withContext existing
            }
            val activeSession = session
                ?: throw SshConnectException("SSH session is not connected")
            if (!activeSession.isConnected) {
                throw SshConnectException("SSH session is disconnected")
            }
            val opened = activeSession.openChannel("sftp") as ChannelSftp
            opened.connect(timeoutSeconds.coerceIn(5, 120) * 1000)
            sftpChannel = opened
            opened
        }
    }

    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            disconnectInternal(updateState = true)
        }
    }

    private fun disconnectInternal(updateState: Boolean) {
        readerJob?.cancel()
        readerJob = null
        runCatching { shellOutput?.close() }
        shellOutput = null
        runCatching { sftpChannel?.disconnect() }
        sftpChannel = null
        runCatching { shellChannel?.disconnect() }
        shellChannel = null
        runCatching { session?.disconnect() }
        session = null
        if (updateState) {
            _state.value = SshConnectionState.DISCONNECTED
        }
    }

    private class PasswordUserInfo(private val secret: String) : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = secret
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = false
        override fun showMessage(message: String?) = Unit
    }

    companion object {
        @Volatile
        private var bcInitialized = false

        private fun ensureBouncyCastleRegistered() {
            if (bcInitialized) return
            synchronized(this) {
                if (bcInitialized) return
                runCatching {
                    Security.removeProvider("BC")
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                }
                bcInitialized = true
            }
        }

        fun computeSha256Fingerprint(keyBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
            val b64 = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            return "SHA256:$b64"
        }

        fun extractSshKeyAlgorithm(keyBytes: ByteArray): String {
            return runCatching {
                if (keyBytes.size < 8) return@runCatching "SSH"
                val len = ByteBuffer.wrap(keyBytes, 0, 4).int
                if (len in 1..(keyBytes.size - 4)) {
                    String(keyBytes, 4, len, Charsets.US_ASCII)
                } else {
                    "SSH"
                }
            }.getOrDefault("SSH")
        }

        private fun mapSshError(error: Throwable, timeoutSeconds: Int): String {
            val msg = (error.message ?: "").lowercase()
            val cause = error.cause
            return when {
                error is SocketTimeoutException ||
                    cause is SocketTimeoutException ||
                    msg.contains("timed out") ||
                    msg.contains("timeout") ->
                    "Connection timed out after ${timeoutSeconds}s"
                error is UnknownHostException ||
                    cause is UnknownHostException ||
                    msg.contains("unknownhost") ||
                    msg.contains("unresolved") ->
                    "Unreachable host: DNS lookup failed"
                msg.contains("auth fail") ||
                    msg.contains("auth cancel") ||
                    msg.contains("userauth") ->
                    "Authentication failed"
                msg.contains("connection refused") ->
                    "Connection refused by remote host"
                msg.contains("network is unreachable") || msg.contains("no route to host") ->
                    "Connection timed out after ${timeoutSeconds}s (host unreachable)"
                else -> error.message?.takeIf { it.isNotBlank() } ?: "SSH connection failed"
            }
        }
    }
}
