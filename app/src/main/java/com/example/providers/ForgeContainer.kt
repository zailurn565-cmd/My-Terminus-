package com.example.providers

import android.content.Context
import com.example.services.CredentialStore
import com.example.services.ForgeDatabase
import com.example.services.HostRepository
import com.example.services.SettingsRepository
import com.example.services.SnippetRepository

class ForgeContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database: ForgeDatabase = ForgeDatabase.getInstance(appContext)

    val credentialStore: CredentialStore = CredentialStore(appContext)
    val hostRepository: HostRepository = HostRepository(
        hostDao = database.hostDao(),
        knownHostDao = database.knownHostDao()
    )
    val snippetRepository: SnippetRepository = SnippetRepository(database.snippetDao())
    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    companion object {
        @Volatile
        private var instance: ForgeContainer? = null

        fun getInstance(context: Context): ForgeContainer {
            return instance ?: synchronized(this) {
                instance ?: ForgeContainer(context).also { instance = it }
            }
        }
    }
}
