package com.example.services

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.Vector

data class RemoteFileEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Int,
    val permissionsString: String,
    val octalPermissions: String,
    val uid: Int,
    val gid: Int
)

class SftpService(private val sshService: SshService) {

    suspend fun resolveHomeDirectory(): String = withContext(Dispatchers.IO) {
        val channel = sshService.getOrOpenSftp()
        synchronized(channel) {
            channel.pwd().ifBlank { "/" }
        }
    }

    suspend fun listDirectory(path: String): List<RemoteFileEntry> = withContext(Dispatchers.IO) {
        val normalized = normalizeRemotePath(path)
        val channel = sshService.getOrOpenSftp()
        val rawEntries: Vector<*> = synchronized(channel) {
            channel.ls(normalized)
        }

        val items = ArrayList<RemoteFileEntry>(rawEntries.size)
        for (raw in rawEntries) {
            val entry = raw as? ChannelSftp.LsEntry ?: continue
            val filename = entry.filename ?: continue
            if (filename == "." || filename == "..") continue
            val attrs: SftpATTRS = entry.attrs ?: continue
            val childPath = joinRemotePath(normalized, filename)
            items.add(
                RemoteFileEntry(
                    name = filename,
                    fullPath = childPath,
                    isDirectory = attrs.isDir,
                    isSymlink = attrs.isLink,
                    sizeBytes = attrs.size,
                    modifiedEpochSeconds = attrs.mTime,
                    permissionsString = attrs.permissionsString ?: formatPermissions(attrs),
                    octalPermissions = String.format(Locale.US, "0%o", attrs.permissions and 0xFFF),
                    uid = attrs.uId,
                    gid = attrs.gId
                )
            )
        }

        items.sortedWith(
            compareByDescending<RemoteFileEntry> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    suspend fun downloadFile(
        remoteFile: RemoteFileEntry,
        destinationDir: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val targetFile = uniqueDestinationFile(destinationDir, remoteFile.name)
        val channel = sshService.getOrOpenSftp()
        val total = remoteFile.sizeBytes.coerceAtLeast(1L)

        FileOutputStream(targetFile).use { output ->
            val monitor = ByteProgressMonitor(total, onProgress)
            synchronized(channel) {
                channel.get(remoteFile.fullPath, output, monitor)
            }
        }
        onProgress(1f)
        targetFile
    }

    suspend fun uploadStream(
        inputStream: InputStream,
        fileName: String,
        totalBytes: Long,
        remoteDirectory: String,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val targetRemotePath = joinRemotePath(normalizeRemotePath(remoteDirectory), fileName)
        val channel = sshService.getOrOpenSftp()
        val safeTotal = totalBytes.coerceAtLeast(1L)
        val monitor = ByteProgressMonitor(safeTotal, onProgress)

        inputStream.use { input ->
            synchronized(channel) {
                channel.put(input, targetRemotePath, monitor, ChannelSftp.OVERWRITE)
            }
        }
        onProgress(1f)
        targetRemotePath
    }

    suspend fun createFolder(parentDirectory: String, folderName: String) = withContext(Dispatchers.IO) {
        val cleanName = folderName.trim().trim('/')
        require(cleanName.isNotEmpty()) { "Folder name cannot be empty" }
        val targetPath = joinRemotePath(normalizeRemotePath(parentDirectory), cleanName)
        val channel = sshService.getOrOpenSftp()
        synchronized(channel) {
            channel.mkdir(targetPath)
        }
    }

    suspend fun renameEntry(oldPath: String, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = newName.trim().trim('/')
        require(cleanName.isNotEmpty()) { "New name cannot be empty" }
        val parent = parentRemotePath(oldPath)
        val newPath = joinRemotePath(parent, cleanName)
        val channel = sshService.getOrOpenSftp()
        synchronized(channel) {
            channel.rename(oldPath, newPath)
        }
    }

    suspend fun deleteEntry(entry: RemoteFileEntry) = withContext(Dispatchers.IO) {
        val channel = sshService.getOrOpenSftp()
        if (entry.isDirectory && !entry.isSymlink) {
            deleteDirectoryRecursive(channel, entry.fullPath)
        } else {
            synchronized(channel) {
                channel.rm(entry.fullPath)
            }
        }
    }

    private fun deleteDirectoryRecursive(channel: ChannelSftp, dirPath: String) {
        val children: Vector<*> = synchronized(channel) { channel.ls(dirPath) }
        for (raw in children) {
            val lsEntry = raw as? ChannelSftp.LsEntry ?: continue
            val name = lsEntry.filename ?: continue
            if (name == "." || name == "..") continue
            val childFull = joinRemotePath(dirPath, name)
            val attrs = lsEntry.attrs
            if (attrs != null && attrs.isDir && !attrs.isLink) {
                deleteDirectoryRecursive(channel, childFull)
            } else {
                synchronized(channel) { channel.rm(childFull) }
            }
        }
        synchronized(channel) {
            channel.rmdir(dirPath)
        }
    }

    private class ByteProgressMonitor(
        private val totalBytes: Long,
        private val onProgress: (Float) -> Unit
    ) : SftpProgressMonitor {
        private var transferred = 0L

        override fun init(op: Int, src: String?, dest: String?, max: Long) {
            transferred = 0L
            onProgress(0f)
        }

        override fun count(count: Long): Boolean {
            transferred += count
            val denom = if (totalBytes > 0L) totalBytes else 1L
            onProgress((transferred.toFloat() / denom.toFloat()).coerceIn(0f, 0.99f))
            return true
        }

        override fun end() {
            onProgress(1f)
        }
    }

    companion object {
        fun normalizeRemotePath(path: String): String {
            val trimmed = path.trim()
            if (trimmed.isEmpty() || trimmed == "/") return "/"
            val collapsed = trimmed.replace(Regex("/+"), "/").trimEnd('/')
            return if (collapsed.startsWith('/')) collapsed else "/$collapsed"
        }

        fun joinRemotePath(parent: String, child: String): String {
            val base = normalizeRemotePath(parent)
            val cleanChild = child.trim('/')
            return if (base == "/") "/$cleanChild" else "$base/$cleanChild"
        }

        fun parentRemotePath(path: String): String {
            val norm = normalizeRemotePath(path)
            if (norm == "/") return "/"
            val lastSlash = norm.lastIndexOf('/')
            return if (lastSlash <= 0) "/" else norm.substring(0, lastSlash)
        }

        private fun uniqueDestinationFile(dir: File, originalName: String): File {
            val candidate = File(dir, originalName)
            if (!candidate.exists()) return candidate
            val dot = originalName.lastIndexOf('.')
            val stem = if (dot > 0) originalName.substring(0, dot) else originalName
            val ext = if (dot > 0) originalName.substring(dot) else ""
            var counter = 1
            while (true) {
                val next = File(dir, "${stem}_$counter$ext")
                if (!next.exists()) return next
                counter++
            }
        }

        private fun formatPermissions(attrs: SftpATTRS): String {
            val p = attrs.permissions
            val type = when {
                attrs.isDir -> 'd'
                attrs.isLink -> 'l'
                else -> '-'
            }
            fun bit(mask: Int, ch: Char) = if ((p and mask) != 0) ch else '-'
            return buildString(10) {
                append(type)
                append(bit(0x100, 'r'))
                append(bit(0x080, 'w'))
                append(bit(0x040, 'x'))
                append(bit(0x020, 'r'))
                append(bit(0x010, 'w'))
                append(bit(0x008, 'x'))
                append(bit(0x004, 'r'))
                append(bit(0x002, 'w'))
                append(bit(0x001, 'x'))
            }
        }
    }
}
