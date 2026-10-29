package com.example.services

import android.content.Context
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

data class TmuxSessionInfo(
    val name: String,
    val windows: Int,
    val attached: Boolean,
    val path: String
)

class VpsAutomationService(
    private val context: Context,
    private val sshService: SshService,
    private val sftpService: SftpService
) {

    suspend fun ensureRunnerInstalled(homeDir: String) = withContext(Dispatchers.IO) {
        val scriptPath = SftpService.joinRemotePath(homeDir, ".forgeshell_auto.sh")
        val scriptBytes = context.resources.openRawResource(R.raw.forgeshell_auto)
            .use { it.readBytes() }
        runCatching {
            sftpService.uploadStream(
                inputStream = ByteArrayInputStream(scriptBytes),
                fileName = ".forgeshell_auto.sh",
                totalBytes = scriptBytes.size.toLong(),
                remoteDirectory = homeDir,
                onProgress = {}
            )
            sshService.execCommand(
                command = buildString {
                    append("chmod +x ")
                    append(shellQuote(scriptPath))
                    append(" && for rc in ~/.bashrc ~/.bash_profile ~/.profile ~/.zshrc; do ")
                    append("[ -f \"\$rc\" ] || touch \"\$rc\" 2>/dev/null; ")
                    append("grep -q '.forgeshell_auto.sh' \"\$rc\" 2>/dev/null || ")
                    append("printf '\\n[ -f ~/.forgeshell_auto.sh ] && . ~/.forgeshell_auto.sh\\n' >> \"\$rc\" 2>/dev/null; ")
                    append("done")
                },
                timeoutSeconds = 20
            )
        }
    }

    suspend fun listTmuxSessions(): List<TmuxSessionInfo> = withContext(Dispatchers.IO) {
        val result = sshService.execCommand(
            command = "tmux list-sessions -F '#{session_name}|#{session_windows}|#{session_attached}|#{session_path}' 2>/dev/null || true",
            timeoutSeconds = 15
        )
        result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('|') }
            .mapNotNull { line ->
                val parts = line.split('|')
                val name = parts.getOrNull(0)?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val windows = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                val attached = (parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0) > 0
                val path = parts.getOrNull(3)?.trim().orEmpty()
                TmuxSessionInfo(
                    name = name,
                    windows = windows,
                    attached = attached,
                    path = path
                )
            }
            .toList()
    }

    suspend fun killTmuxSession(sessionName: String) = withContext(Dispatchers.IO) {
        val safeName = sanitizeSessionName(sessionName)
        sshService.execCommand("tmux kill-session -t ${shellQuote(safeName)} 2>/dev/null || true", 15)
    }

    suspend fun ensureTmuxInstalled() = withContext(Dispatchers.IO) {
        sshService.execCommand(
            command = buildString {
                append("if ! command -v tmux >/dev/null 2>&1; then ")
                append("export DEBIAN_FRONTEND=noninteractive; ")
                append("if command -v apt-get >/dev/null 2>&1; then ")
                append("(apt-get update -y && apt-get install -y tmux) || (sudo -n apt-get update -y && sudo -n apt-get install -y tmux); ")
                append("elif command -v apk >/dev/null 2>&1; then apk add --no-cache tmux; ")
                append("elif command -v dnf >/dev/null 2>&1; then dnf install -y tmux; ")
                append("elif command -v yum >/dev/null 2>&1; then yum install -y tmux; ")
                append("fi; fi")
            },
            timeoutSeconds = 120
        )
    }

    suspend fun createOrEnsureTmuxSession(
        sessionName: String,
        workingDir: String
    ): String = withContext(Dispatchers.IO) {
        val safeName = sanitizeSessionName(sessionName)
        ensureTmuxInstalled()
        val normDir = SftpService.normalizeRemotePath(workingDir)
        sshService.execCommand(
            command = buildString {
                append("mkdir -p ${shellQuote(normDir)} && ")
                append("if ! tmux has-session -t ${shellQuote(safeName)} 2>/dev/null; then ")
                append("tmux new-session -d -s ${shellQuote(safeName)} -c ${shellQuote(normDir)} ")
                append("\"bash --rcfile ~/.bashrc -i\" 2>/dev/null || ")
                append("tmux new-session -d -s ${shellQuote(safeName)} -c ${shellQuote(normDir)}; ")
                append("fi")
            },
            timeoutSeconds = 30
        )
        safeName
    }

    suspend fun unzipArchiveAndPrepareTmux(
        targetFolder: String,
        uploadedZipRemotePath: String,
        sessionName: String,
        onStatus: (String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val normFolder = SftpService.normalizeRemotePath(targetFolder)
        val safeSession = sanitizeSessionName(sessionName)

        onStatus("Checking tmux & unzip tools on VPS…")
        sshService.execCommand(
            command = buildString {
                append("export DEBIAN_FRONTEND=noninteractive; ")
                append("NEED_PKGS=''; ")
                append("command -v tmux >/dev/null 2>&1 || NEED_PKGS=\"\$NEED_PKGS tmux\"; ")
                append("command -v unzip >/dev/null 2>&1 || NEED_PKGS=\"\$NEED_PKGS unzip\"; ")
                append("if [ -n \"\$NEED_PKGS\" ]; then ")
                append("if command -v apt-get >/dev/null 2>&1; then ")
                append("(apt-get update -y && apt-get install -y \$NEED_PKGS) || (sudo -n apt-get update -y && sudo -n apt-get install -y \$NEED_PKGS); ")
                append("elif command -v apk >/dev/null 2>&1; then apk add --no-cache \$NEED_PKGS; ")
                append("elif command -v dnf >/dev/null 2>&1; then dnf install -y \$NEED_PKGS; ")
                append("elif command -v yum >/dev/null 2>&1; then yum install -y \$NEED_PKGS; ")
                append("fi; fi")
            },
            timeoutSeconds = 150
        )

        onStatus("Extracting .zip archive in $normFolder…")
        val unzipRes = sshService.execCommand(
            command = buildString {
                append("cd ${shellQuote(normFolder)} && ")
                append("if command -v unzip >/dev/null 2>&1; then ")
                append("unzip -o ${shellQuote(uploadedZipRemotePath)} -d ${shellQuote(normFolder)}; ")
                append("elif command -v python3 >/dev/null 2>&1; then ")
                append("python3 -m zipfile -e ${shellQuote(uploadedZipRemotePath)} ${shellQuote(normFolder)}; ")
                append("else exit 12; fi")
            },
            timeoutSeconds = 120
        )
        if (unzipRes.exitCode != 0) {
            throw IllegalStateException(
                unzipRes.stderr.ifBlank { "Failed to extract zip archive (exit ${unzipRes.exitCode})" }
            )
        }

        val detectDirRes = sshService.execCommand(
            command = buildString {
                append("python3 - ${shellQuote(normFolder)} ${shellQuote(uploadedZipRemotePath)} << 'PYEOF'\n")
                append("import os, sys\n")
                append("base = sys.argv[1]\n")
                append("zip_name = os.path.basename(sys.argv[2])\n")
                append("items = [x for x in os.listdir(base) if x != zip_name and not x.startswith('.') and x != '__MACOSX']\n")
                append("if len(items) == 1 and os.path.isdir(os.path.join(base, items[0])):\n")
                append("    print(os.path.join(base, items[0]))\n")
                append("else:\n")
                append("    print(base)\n")
                append("PYEOF")
            },
            timeoutSeconds = 15
        )
        val resolvedProjectDir = detectDirRes.stdout.lines()
            .firstOrNull { it.trim().startsWith("/") }
            ?.trim()
            ?: normFolder

        onStatus("Starting tmux session \"$safeSession\"…")
        createOrEnsureTmuxSession(safeSession, resolvedProjectDir)

        safeSession to resolvedProjectDir
    }

    suspend fun unzipFileInPlace(remoteZipPath: String) = withContext(Dispatchers.IO) {
        val parentDir = SftpService.parentRemotePath(remoteZipPath)
        val res = sshService.execCommand(
            command = buildString {
                append("cd ${shellQuote(parentDir)} && ")
                append("(unzip -o ${shellQuote(remoteZipPath)} -d ${shellQuote(parentDir)} || ")
                append("python3 -m zipfile -e ${shellQuote(remoteZipPath)} ${shellQuote(parentDir)})")
            },
            timeoutSeconds = 120
        )
        if (res.exitCode != 0) {
            throw IllegalStateException(res.stderr.ifBlank { "Unzip failed" })
        }
    }

    companion object {
        fun sanitizeSessionName(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[^A-Za-z0-9_\\-]"), "-")
                .trim('-')
            return cleaned.ifEmpty { "vps-app" }.take(40)
        }

        fun shellQuote(value: String): String {
            return "'" + value.replace("'", "'\"'\"'") + "'"
        }

        fun buildAttachTmuxShellLine(sessionName: String, workingDir: String? = null): String {
            val safe = sanitizeSessionName(sessionName)
            val cdPart = if (!workingDir.isNullOrBlank()) {
                "cd ${shellQuote(workingDir)} && "
            } else {
                ""
            }
            return "${cdPart}[ -f ~/.forgeshell_auto.sh ] && . ~/.forgeshell_auto.sh; tmux attach-session -t ${shellQuote(safe)} || tmux new-session -s ${shellQuote(safe)}\r"
        }
    }
}
