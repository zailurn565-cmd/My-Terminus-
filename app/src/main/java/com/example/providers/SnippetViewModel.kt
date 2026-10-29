package com.example.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.models.Snippet
import com.example.services.SnippetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnippetViewModel(
    private val snippetRepository: SnippetRepository
) : ViewModel() {

    val snippets: StateFlow<List<Snippet>> = snippetRepository.snippetsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun upsertSnippet(snippet: Snippet) {
        viewModelScope.launch {
            snippetRepository.upsert(snippet)
        }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch {
            snippetRepository.delete(id)
        }
    }

    companion object {
        fun factory(container: ForgeContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SnippetViewModel(container.snippetRepository) as T
                }
            }
    }
}
