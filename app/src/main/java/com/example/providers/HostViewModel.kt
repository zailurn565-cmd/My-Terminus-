package com.example.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.models.AuthType
import com.example.models.HostProfile
import com.example.services.CredentialBundle
import com.example.services.CredentialStore
import com.example.services.HostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class HostViewModel(
    private val hostRepository: HostRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    val allHosts: StateFlow<List<HostProfile>> = hostRepository.hostsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val availableGroups: StateFlow<List<String>> = combine(allHosts) { (hosts) ->
        hosts.mapNotNull { it.group?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sortedBy { it.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val filteredHosts: StateFlow<List<HostProfile>> = combine(
        allHosts,
        _searchQuery,
        _selectedGroup
    ) { hosts, query, group ->
        val q = query.trim().lowercase()
        hosts.filter { host ->
            val matchesGroup = group == null || host.group?.equals(group, ignoreCase = true) == true
            val matchesQuery = q.isEmpty() ||
                host.label.lowercase().contains(q) ||
                host.host.lowercase().contains(q) ||
                host.username.lowercase().contains(q) ||
                host.connectionSummary.lowercase().contains(q) ||
                (host.group?.lowercase()?.contains(q) == true)
            matchesGroup && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private var lastDeletedHost: HostProfile? = null
    private var lastDeletedCredentials: CredentialBundle? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedGroup(group: String?) {
        _selectedGroup.value = group
    }

    suspend fun getHostById(id: String): HostProfile? = hostRepository.getById(id)

    suspend fun loadCredentials(hostId: String): CredentialBundle =
        credentialStore.readBundle(hostId)

    suspend fun saveHost(
        profile: HostProfile,
        password: String?,
        privateKeyPem: String?,
        passphrase: String?
    ) {
        hostRepository.upsert(profile)
        when (profile.authType) {
            AuthType.PASSWORD -> {
                credentialStore.deleteAll(profile.id)
                if (!password.isNullOrEmpty()) {
                    credentialStore.savePassword(profile.id, password)
                }
            }
            AuthType.PRIVATE_KEY -> {
                credentialStore.deleteAll(profile.id)
                if (!privateKeyPem.isNullOrBlank()) {
                    credentialStore.savePrivateKey(profile.id, privateKeyPem)
                }
                credentialStore.saveKeyPassphrase(profile.id, passphrase)
            }
        }
    }

    fun deleteHost(profile: HostProfile, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            lastDeletedHost = profile
            lastDeletedCredentials = credentialStore.readBundle(profile.id)
            hostRepository.delete(profile.id)
            credentialStore.deleteAll(profile.id)
            onDeleted()
        }
    }

    fun undoDelete() {
        val host = lastDeletedHost ?: return
        val creds = lastDeletedCredentials
        lastDeletedHost = null
        lastDeletedCredentials = null
        viewModelScope.launch {
            hostRepository.upsert(host)
            if (creds != null) {
                credentialStore.restoreBundle(host.id, creds)
            }
        }
    }

    fun duplicateHost(profile: HostProfile, onComplete: (HostProfile) -> Unit = {}) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val duplicated = profile.copy(
                id = newId,
                label = "${profile.label} (Copy)",
                lastConnectedAt = null
            )
            val creds = credentialStore.readBundle(profile.id)
            hostRepository.upsert(duplicated)
            credentialStore.restoreBundle(newId, creds)
            onComplete(duplicated)
        }
    }

    companion object {
        fun factory(container: ForgeContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HostViewModel(
                        hostRepository = container.hostRepository,
                        credentialStore = container.credentialStore
                    ) as T
                }
            }
    }
}
