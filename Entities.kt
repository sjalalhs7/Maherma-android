package com.maherma.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "threads")
data class ThreadEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val updatedAt: Long)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(ThreadEntity::class, ["id"], ["threadId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("threadId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long, val kind: String, val text: String,
    val status: String?, val payload: String?, val detail: String?, val chips: String, val createdAt: Long,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val notes: String = "", val dueAt: Long? = null,
    val repeat: String = "NONE", val done: Boolean = false, val createdAt: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val body: String, val createdAt: Long, val updatedAt: Long,
)

@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, val text: String, val source: String,
    val createdAt: Long, val lastUsed: Long = 0, val pinned: Boolean = false, val enabled: Boolean = true,
)

@Entity(tableName = "audit")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val kind: String, val title: String, val detail: String, val status: String,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM threads ORDER BY updatedAt DESC") fun threads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE id = :id") suspend fun thread(id: Long): ThreadEntity?
    @Query("SELECT * FROM messages WHERE threadId = :id ORDER BY createdAt ASC, id ASC") fun messages(id: Long): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE threadId = :id ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(id: Long, limit: Int): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun message(id: Long): MessageEntity?
    @Insert suspend fun insertThread(t: ThreadEntity): Long
    @Insert suspend fun insertMessage(m: MessageEntity): Long
    @Query("UPDATE messages SET status = :status, detail = :detail WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, detail: String?)
    @Query("UPDATE threads SET updatedAt = :t WHERE id = :id") suspend fun touch(id: Long, t: Long)
    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteMessage(id: Long)
    @Query("DELETE FROM threads WHERE id = :id") suspend fun deleteThread(id: Long)
    @Query("DELETE FROM threads") suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, id ASC")
    fun observeOpen(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, id ASC")
    suspend fun open(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun byId(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND dueAt > :now") suspend fun scheduled(now: Long): List<TaskEntity>
    @Query("SELECT * FROM tasks") suspend fun all(): List<TaskEntity>
    @Insert suspend fun insert(t: TaskEntity): Long
    @Update suspend fun update(t: TaskEntity)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tasks") suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC") fun observe(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE title LIKE :q OR body LIKE :q ORDER BY updatedAt DESC LIMIT 10")
    suspend fun search(q: String): List<NoteEntity>
    @Insert suspend fun insert(n: NoteEntity): Long
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM notes") suspend fun deleteAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory ORDER BY pinned DESC, createdAt DESC") fun observe(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memory WHERE enabled = 1") suspend fun enabled(): List<MemoryEntity>
    @Query("SELECT * FROM memory") suspend fun all(): List<MemoryEntity>
    @Insert suspend fun insert(m: MemoryEntity): Long
    @Update suspend fun update(m: MemoryEntity)
    @Query("UPDATE memory SET lastUsed = :t WHERE id IN (:ids)") suspend fun markUsed(ids: List<Long>, t: Long)
    @Query("DELETE FROM memory WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM memory") suspend fun deleteAll()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit ORDER BY ts DESC LIMIT 200") fun observe(): Flow<List<AuditEntity>>
    @Insert suspend fun insert(a: AuditEntity): Long
    @Query("DELETE FROM audit") suspend fun deleteAll()
}

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, TaskEntity::class, NoteEntity::class, MemoryEntity::class, AuditEntity::class],
    version = 1, exportSchema = false,
)
abstract class MahermaDb : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun memoryDao(): MemoryDao
    abstract fun auditDao(): AuditDao
}
