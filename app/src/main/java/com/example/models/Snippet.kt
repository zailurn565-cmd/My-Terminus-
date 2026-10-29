package com.example.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val command: String,
    val hostId: String? = null
)
