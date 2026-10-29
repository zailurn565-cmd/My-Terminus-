package com.example.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.models.AppSettings
import com.example.models.AppThemeMode
import com.example.models.CursorStyle
import com.example.models.KnownHostEntry
import com.example.services.CredentialStore
import com.example.services.HostRepository
import com.example.services.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val hostRepository: HostRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    val knownHosts: StateFlow<List<KnownHostEntry>> = hostRepository.knownHostsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun setTerminalFontSize(size: Float) {
        viewModelScope.launch { settingsRepository.updateTerminalFontSize(size) }
    }

    fun setTerminalFontFamily(family: String) {
        viewModelScope.launch { settingsRepository.updateTerminalFontFamily(family) }
    }

    fun setCursorStyle(style: CursorStyle) {
        viewModelScope.launch { settingsRepository.updateCursorStyle(style) }
    }

    fun setDefaultPort(port: Int) {
        viewModelScope.launch { settingsRepository.updateDefaultPort(port) }
    }

    fun setDefaultUsername(username: String) {
        viewModelScope.launch { settingsRepository.updateDefaultUsername(username) }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoReconnect(enabled) }
    }

    fun removeKnownHost(endpointKey: String) {
        viewModelScope.launch { hostRepository.deleteKnownHost(endpointKey) }
    }

    fun clearAllKnownHosts(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            hostRepository.clearKnownHosts()
            onDone()
        }
    }

    fun clearAllCredentials(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            credentialStore.clearVault()
            onDone()
        }
    }

    companion object {
        fun factory(container: ForgeContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = container.settingsRepository,
                        hostRepository = container.hostRepository,
                        credentialStore = container.credentialStore
                    ) as T
                }
            }
    }
}
