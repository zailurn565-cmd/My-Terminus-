package com.example.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Validators {
    private val hostRegex = Regex(
        """^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$"""
    )
    private val ipv4Regex = Regex(
        """^((25[0-5]|(2[0-4]|1\d|[1-9]|)\d)\.?\b){4}$"""
    )
    private val ipv6Regex = Regex("""^[0-9a-fA-F:]+$""")

    fun validateLabel(label: String): String? {
        if (label.trim().isEmpty()) return "Host label is required"
        if (label.trim().length > 64) return "Label must be 64 characters or fewer"
        return null
    }

    fun validateHost(host: String): String? {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return "Hostname or IP address is required"
        if (trimmed.contains(' ')) return "Hostname cannot contain spaces"
        val valid = ipv4Regex.matches(trimmed) ||
            (trimmed.contains(':') && ipv6Regex.matches(trimmed)) ||
            hostRegex.matches(trimmed)
        return if (valid) null else "Enter a valid hostname or IP address"
    }

    fun validatePort(portText: String): String? {
        val port = portText.trim().toIntOrNull()
            ?: return "Port must be a number"
        if (port !in 1..65535) return "Port must be between 1 and 65535"
        return null
    }

    fun validateUsername(username: String): String? {
        if (username.trim().isEmpty()) return "Username is required"
        if (username.trim().contains(' ')) return "Username cannot contain spaces"
        return null
    }

    fun validatePositiveSeconds(value: String, fieldName: String, max: Int = 600): String? {
        val parsed = value.trim().toIntOrNull()
            ?: return "$fieldName must be a number"
        if (parsed !in 1..max) return "$fieldName must be between 1 and ${max}s"
        return null
    }

    fun validatePrivateKeyPem(pem: String): String? {
        val trimmed = pem.trim()
        if (trimmed.isEmpty()) return "Private key PEM/OpenSSH content is required"
        if (!trimmed.contains("-----BEGIN ") || !trimmed.contains("PRIVATE KEY-----")) {
            return "Key must be a valid PEM or OpenSSH private key block"
        }
        return null
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(5)
        val unit = "KMGTPE"[exp - 1]
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, unit)
    }

    fun formatRelativeTime(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return "Never connected"
        val diffSeconds = ((System.currentTimeMillis() - epochMillis) / 1000L).coerceAtLeast(0L)
        return when {
            diffSeconds < 60L -> "Just now"
            diffSeconds < 3600L -> "${diffSeconds / 60L}m ago"
            diffSeconds < 86400L -> "${diffSeconds / 3600L}h ago"
            diffSeconds < 604800L -> "${diffSeconds / 86400L}d ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))
        }
    }

    fun formatModifiedEpochSeconds(epochSeconds: Int): String {
        if (epochSeconds <= 0) return ""
        val millis = epochSeconds.toLong() * 1000L
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }
}
