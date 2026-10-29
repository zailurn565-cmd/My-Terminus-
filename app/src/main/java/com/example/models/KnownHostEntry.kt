package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_hosts")
data class KnownHostEntry(
    @PrimaryKey
    val endpointKey: String,
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val trustedAt: Long = System.currentTimeMillis()
)
