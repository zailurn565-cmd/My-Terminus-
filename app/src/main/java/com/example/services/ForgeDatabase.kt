package com.example.services

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.models.AuthType
import com.example.models.HostProfile
import com.example.models.KnownHostEntry
import com.example.models.Snippet
import kotlinx.coroutines.flow.Flow

class ForgeTypeConverters {
    @TypeConverter
    fun fromAuthType(authType: AuthType): String = authType.name

    @TypeConverter
    fun toAuthType(value: String): AuthType =
        runCatching { AuthType.valueOf(value) }.getOrDefault(AuthType.PASSWORD)
}

@Dao
interface HostDao {
    @Query(
        """
        SELECT * FROM hosts
        ORDER BY CASE WHEN lastConnectedAt IS NULL THEN 1 ELSE 0 END ASC,
                 lastConnectedAt DESC,
                 label COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<HostProfile>>

    @Query(
        """
        SELECT * FROM hosts
        ORDER BY CASE WHEN lastConnectedAt IS NULL THEN 1 ELSE 0 END ASC,
                 lastConnectedAt DESC,
                 label COLLATE NOCASE ASC
        """
    )
    suspend fun getAll(): List<HostProfile>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HostProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: HostProfile)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestamp: Long)

    @Query("UPDATE hosts SET keyFingerprint = :fingerprint WHERE id = :id")
    suspend fun updateKeyFingerprint(id: String, fingerprint: String)
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets ORDER BY label COLLATE NOCASE ASC")
    suspend fun getAll(): List<Snippet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snippet: Snippet)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts ORDER BY trustedAt DESC")
    fun observeAll(): Flow<List<KnownHostEntry>>

    @Query("SELECT * FROM known_hosts WHERE endpointKey = :endpointKey LIMIT 1")
    suspend fun getByEndpoint(endpointKey: String): KnownHostEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: KnownHostEntry)

    @Query("DELETE FROM known_hosts WHERE endpointKey = :endpointKey")
    suspend fun deleteByEndpoint(endpointKey: String)

    @Query("DELETE FROM known_hosts")
    suspend fun deleteAll()
}

@Database(
    entities = [HostProfile::class, Snippet::class, KnownHostEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ForgeTypeConverters::class)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun snippetDao(): SnippetDao
    abstract fun knownHostDao(): KnownHostDao

    companion object {
        @Volatile
        private var instance: ForgeDatabase? = null

        fun getInstance(context: Context): ForgeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ForgeDatabase::class.java,
                    "forgeshell_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
