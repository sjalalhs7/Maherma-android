package com.maherma.app.data.repo

import com.maherma.app.data.db.AuditDao
import com.maherma.app.data.db.AuditEntity
import com.maherma.app.data.db.MemoryDao
import com.maherma.app.data.db.MemoryEntity
import com.maherma.app.data.db.NoteDao
import com.maherma.app.data.db.NoteEntity
import com.maherma.app.data.db.TaskDao
import com.maherma.app.data.db.TaskEntity
import com.maherma.app.device.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(private val dao: TaskDao, private val scheduler: ReminderScheduler) {
    fun observeOpen(): Flow<List<TaskEntity>> = dao.observeOpen()
    suspend fun open(): List<TaskEntity> = dao.open()
    suspend fun get(id: Long) = dao.byId(id)

    /** Returns the new task and whether its reminder is exact (false = approximate or none). */
    suspend fun create(title: String, dueAt: Long?, repeat: String, notes: String): Pair<TaskEntity, Boolean> {
        val t = TaskEntity(title = title, notes = notes, dueAt = dueAt, repeat = repeat, createdAt = System.currentTimeMillis())
        val saved = t.copy(id = dao.insert(t))
        val exact = dueAt?.let { scheduler.schedule(saved) } ?: false
        return saved to exact
    }

    suspend fun complete(id: Long): Boolean {
        val t = dao.byId(id) ?: return false
        scheduler.cancel(id)
        dao.update(t.copy(done = true))
        return true
    }

    suspend fun delete(id: Long) { scheduler.cancel(id); dao.delete(id) }
    suspend fun deleteAll() { dao.all().forEach { scheduler.cancel(it.id) }; dao.deleteAll() }
}

@Singleton
class NoteRepository @Inject constructor(private val dao: NoteDao) {
    fun observe(): Flow<List<NoteEntity>> = dao.observe()
    suspend fun create(title: String, body: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title.ifBlank { body.take(40) }, body = body, createdAt = now, updatedAt = now))
    }
    suspend fun search(q: String): List<NoteEntity> = dao.search("%${q.trim()}%")
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
}

@Singleton
class MemoryRepository @Inject constructor(private val dao: MemoryDao) {
    fun observe(): Flow<List<MemoryEntity>> = dao.observe()

    suspend fun add(type: String, text: String, source: String): Long =
        dao.insert(MemoryEntity(type = type, text = text.trim(), source = source, createdAt = System.currentTimeMillis()))

    suspend fun update(m: MemoryEntity) = dao.update(m)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()

    /** Removes enabled or disabled memories whose text contains [query]; returns how many. */
    suspend fun forget(query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val hits = dao.all().filter { it.text.lowercase().contains(q) }
        hits.forEach { dao.delete(it.id) }
        return hits.size
    }

    /** Small, explainable ranking: pinned first, then keyword overlap, then recency. At most [k] items. */
    suspend fun relevant(query: String, k: Int): List<MemoryEntity> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val ranked = dao.enabled().sortedByDescending { m ->
            val overlap = words.count { m.text.lowercase().contains(it) }
            (if (m.pinned) 100 else 0) + overlap * 10 + (m.createdAt / 86_400_000L % 10).toInt()
        }.take(k)
        if (ranked.isNotEmpty()) dao.markUsed(ranked.map { it.id }, System.currentTimeMillis())
        return ranked
    }
}

@Singleton
class AuditRepository @Inject constructor(private val dao: AuditDao) {
    fun observe(): Flow<List<AuditEntity>> = dao.observe()
    suspend fun log(kind: String, title: String, detail: String, status: String) {
        dao.insert(AuditEntity(ts = System.currentTimeMillis(), kind = kind, title = title, detail = detail, status = status))
    }
    suspend fun clear() = dao.deleteAll()
}
