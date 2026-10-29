package com.example.services

import com.example.models.HostProfile
import com.example.models.KnownHostEntry
import kotlinx.coroutines.flow.Flow

class HostRepository(
    private val hostDao: HostDao,
    private val knownHostDao: KnownHostDao
) {
    val hostsFlow: Flow<List<HostProfile>> = hostDao.observeAll()
    val knownHostsFlow: Flow<List<KnownHostEntry>> = knownHostDao.observeAll()

    suspend fun all(): List<HostProfile> = hostDao.getAll()

    suspend fun getById(id: String): HostProfile? = hostDao.getById(id)

    suspend fun upsert(profile: HostProfile) = hostDao.upsert(profile)

    suspend fun delete(id: String) = hostDao.deleteById(id)

    suspend fun touchLastConnected(id: String, timestamp: Long = System.currentTimeMillis()) {
        hostDao.updateLastConnected(id, timestamp)
    }

    suspend fun updateHostFingerprint(id: String, fingerprint: String) {
        hostDao.updateKeyFingerprint(id, fingerprint)
    }

    suspend fun getKnownHost(host: String, port: Int): KnownHostEntry? {
        return knownHostDao.getByEndpoint(endpointKey(host, port))
    }

    suspend fun trustHostKey(
        host: String,
        port: Int,
        algorithm: String,
        fingerprint: String,
        hostProfileId: String? = null
    ) {
        knownHostDao.upsert(
            KnownHostEntry(
                endpointKey = endpointKey(host, port),
                host = host,
                port = port,
                algorithm = algorithm,
                fingerprint = fingerprint,
                trustedAt = System.currentTimeMillis()
            )
        )
        if (hostProfileId != null) {
            hostDao.updateKeyFingerprint(hostProfileId, fingerprint)
        }
    }

    suspend fun deleteKnownHost(endpointKey: String) {
        knownHostDao.deleteByEndpoint(endpointKey)
    }

    suspend fun clearKnownHosts() {
        knownHostDao.deleteAll()
    }

    companion object {
        fun endpointKey(host: String, port: Int): String = "${host.trim().lowercase()}:$port"
    }
}
