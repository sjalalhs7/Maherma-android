package com.maherma.app.data.repo

import com.maherma.app.data.db.ChatDao
import com.maherma.app.data.db.MessageEntity
import com.maherma.app.data.db.ThreadEntity
import com.maherma.app.domain.model.ActionStatus
import com.maherma.app.domain.model.ChatItem
import com.maherma.app.domain.model.ChatThread
import com.maherma.app.domain.model.ItemKind
import com.maherma.app.domain.model.Turn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val SEP = "\u001F"

@Singleton
class ConversationRepository @Inject constructor(private val dao: ChatDao) {
    fun threads(): Flow<List<ChatThread>> = dao.threads().map { l -> l.map { ChatThread(it.id, it.title, it.updatedAt) } }
    fun messages(threadId: Long): Flow<List<ChatItem>> = dao.messages(threadId).map { l -> l.map { it.toItem() } }
    suspend fun createThread(title: String): Long = dao.insertThread(ThreadEntity(title = title.ifBlank { "New chat" }, updatedAt = now()))
    suspend fun exists(threadId: Long) = dao.thread(threadId) != null

    suspend fun add(
        threadId: Long, kind: ItemKind, text: String,
        status: ActionStatus? = null, payload: String? = null, detail: String? = null, chips: List<String> = emptyList(),
    ): Long {
        val t = now()
        val id = dao.insertMessage(
            MessageEntity(threadId = threadId, kind = kind.name, text = text, status = status?.name, payload = payload,
                detail = detail, chips = chips.joinToString(SEP), createdAt = t)
        )
        dao.touch(threadId, t)
        return id
    }

    suspend fun get(id: Long): ChatItem? = dao.message(id)?.toItem()
    suspend fun update(id: Long, status: ActionStatus, detail: String?) = dao.updateStatus(id, status.name, detail)
    suspend fun delete(id: Long) = dao.deleteMessage(id)
    suspend fun deleteThread(id: Long) = dao.deleteThread(id)
    suspend fun clearAll() = dao.deleteAll()

    /** Chronological text context. Action cards are included so follow-ups ("call him") resolve. */
    suspend fun history(threadId: Long, limit: Int): List<Turn> =
        dao.recent(threadId, limit).asReversed().mapNotNull { e ->
            when (ItemKind.valueOf(e.kind)) {
                ItemKind.USER -> Turn("user", e.text)
                ItemKind.ASSISTANT -> Turn("model", e.text)
                ItemKind.ACTION -> Turn("model", "[Phone action – ${e.status?.lowercase()}] ${e.text}")
                ItemKind.CARD, ItemKind.ERROR -> null
            }
        }

    private fun now() = System.currentTimeMillis()
    private fun MessageEntity.toItem() = ChatItem(
        id = id, threadId = threadId, kind = ItemKind.valueOf(kind), text = text,
        status = status?.let { ActionStatus.valueOf(it) }, payload = payload, detail = detail,
        chips = if (chips.isEmpty()) emptyList() else chips.split(SEP), createdAt = createdAt,
    )
}
