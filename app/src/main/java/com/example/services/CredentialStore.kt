package com.example.services

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CredentialBundle(
    val password: String?,
    val privateKey: String?,
    val passphrase: String?
)

class CredentialStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefs(appContext)
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                VAULT_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // If keystore keys were invalidated after OS restore, reset corrupted vault file cleanly
            context.deleteSharedPreferences(VAULT_FILE_NAME)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                VAULT_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    suspend fun savePassword(hostId: String, password: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(pwKey(hostId), password).apply()
    }

    suspend fun readPassword(hostId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(pwKey(hostId), null)?.takeIf { it.isNotEmpty() }
    }

    suspend fun savePrivateKey(hostId: String, pem: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(pemKey(hostId), pem.trim()).apply()
    }

    suspend fun readPrivateKey(hostId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(pemKey(hostId), null)?.takeIf { it.isNotBlank() }
    }

    suspend fun saveKeyPassphrase(hostId: String, passphrase: String?) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        if (passphrase.isNullOrEmpty()) {
            editor.remove(passKey(hostId))
        } else {
            editor.putString(passKey(hostId), passphrase)
        }
        editor.apply()
    }

    suspend fun readKeyPassphrase(hostId: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(passKey(hostId), null)?.takeIf { it.isNotEmpty() }
    }

    suspend fun readBundle(hostId: String): CredentialBundle = withContext(Dispatchers.IO) {
        CredentialBundle(
            password = prefs.getString(pwKey(hostId), null),
            privateKey = prefs.getString(pemKey(hostId), null),
            passphrase = prefs.getString(passKey(hostId), null)
        )
    }

    suspend fun restoreBundle(hostId: String, bundle: CredentialBundle) = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        bundle.password?.let { editor.putString(pwKey(hostId), it) }
        bundle.privateKey?.let { editor.putString(pemKey(hostId), it) }
        bundle.passphrase?.let { editor.putString(passKey(hostId), it) }
        editor.apply()
    }

    suspend fun deleteAll(hostId: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(pwKey(hostId))
            .remove(pemKey(hostId))
            .remove(passKey(hostId))
            .apply()
    }

    suspend fun clearVault() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun pwKey(hostId: String) = "cred_pw_$hostId"
    private fun pemKey(hostId: String) = "cred_pem_$hostId"
    private fun passKey(hostId: String) = "cred_pass_$hostId"

    companion object {
        private const val VAULT_FILE_NAME = "forgeshell_secure_vault"
    }
}
