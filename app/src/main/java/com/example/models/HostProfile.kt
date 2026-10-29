package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "hosts")
data class HostProfile(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: AuthType = AuthType.PASSWORD,
    val keyFingerprint: String? = null,
    val group: String? = null,
    val colorTagValue: Long? = 0xFFB87333L,
    val initialCommand: String? = null,
    val connectTimeoutSeconds: Int = 15,
    val keepaliveSeconds: Int = 30,
    val lastConnectedAt: Long? = null
) {
    val connectionSummary: String
        get() = "$username@$host:$port"
}
