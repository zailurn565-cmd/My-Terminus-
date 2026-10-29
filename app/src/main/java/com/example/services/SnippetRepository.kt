package com.example.services

import com.example.models.Snippet
import kotlinx.coroutines.flow.Flow

class SnippetRepository(private val snippetDao: SnippetDao) {
    val snippetsFlow: Flow<List<Snippet>> = snippetDao.observeAll()

    suspend fun all(): List<Snippet> = snippetDao.getAll()

    suspend fun upsert(snippet: Snippet) = snippetDao.upsert(snippet)

    suspend fun delete(id: String) = snippetDao.deleteById(id)
}
