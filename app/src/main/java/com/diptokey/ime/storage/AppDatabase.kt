package com.diptokey.ime.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ----- Entities -----

@Entity(tableName = "clipboard_entries")
data class ClipboardEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
    val pinned: Boolean = false,
    val isSnippet: Boolean = false,
    val label: String? = null
)

@Entity(tableName = "ai_history")
data class AIHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val response: String,
    val provider: String,
    val model: String,
    val createdAt: Long
)

// ----- DAOs -----

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<ClipboardEntryEntity>>

    @Query("SELECT * FROM clipboard_entries ORDER BY pinned DESC, createdAt DESC")
    suspend fun getAll(): List<ClipboardEntryEntity>

    @Query("SELECT * FROM clipboard_entries WHERE text LIKE '%' || :q || '%' ORDER BY pinned DESC, createdAt DESC")
    suspend fun search(q: String): List<ClipboardEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntryEntity): Long

    @Query("UPDATE clipboard_entries SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE pinned = 0")
    suspend fun clearUnpinned()

    @Query("DELETE FROM clipboard_entries")
    suspend fun clearAll()

    @Query("DELETE FROM clipboard_entries WHERE createdAt < :cutoff AND pinned = 0")
    suspend fun pruneOlderThan(cutoff: Long)
}

@Dao
interface AIHistoryDao {
    @Query("SELECT * FROM ai_history ORDER BY createdAt DESC")
    suspend fun getAll(): List<AIHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AIHistoryEntity): Long

    @Query("DELETE FROM ai_history")
    suspend fun clearAll()
}

// ----- DB -----

@Database(
    entities = [ClipboardEntryEntity::class, AIHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun aiHistoryDao(): AIHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diptokey.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
