package com.maherma.app.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maherma.app.data.db.MemoryEntity
import com.maherma.app.data.prefs.AppSettings
import com.maherma.app.data.prefs.SettingsStore
import com.maherma.app.data.repo.AuditRepository
import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.data.repo.MemoryRepository
import com.maherma.app.data.repo.NoteRepository
import com.maherma.app.data.repo.TaskRepository
import com.maherma.app.voice.SessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val memory: MemoryRepository,
    private val audit: AuditRepository,
    private val conversations: ConversationRepository,
    private val store: SettingsStore,
    private val session: SessionController,
) : ViewModel() {
    private val keep = SharingStarted.WhileSubscribed(5_000)
    val openTasks = tasks.observeOpen().stateIn(viewModelScope, keep, emptyList())
    val noteList = notes.observe().stateIn(viewModelScope, keep, emptyList())
    val memories = memory.observe().stateIn(viewModelScope, keep, emptyList())
    val auditLog = audit.observe().stateIn(viewModelScope, keep, emptyList())
    val settings = store.settings.stateIn(viewModelScope, keep, AppSettings())

    fun addTask(title: String) = viewModelScope.launch { if (title.isNotBlank()) tasks.create(title.trim(), null, "NONE", "") }
    fun completeTask(id: Long) = viewModelScope.launch { tasks.complete(id) }
    fun deleteTask(id: Long) = viewModelScope.launch { tasks.delete(id) }

    fun addNote(body: String) = viewModelScope.launch { if (body.isNotBlank()) notes.create("", body.trim()) }
    fun deleteNote(id: Long) = viewModelScope.launch { notes.delete(id) }

    fun addMemory(text: String) = viewModelScope.launch { if (text.isNotBlank()) memory.add("FACT", text, "manual") }
    fun setMemoryEnabled(m: MemoryEntity, v: Boolean) = viewModelScope.launch { memory.update(m.copy(enabled = v)) }
    fun setMemoryPinned(m: MemoryEntity, v: Boolean) = viewModelScope.launch { memory.update(m.copy(pinned = v)) }
    fun deleteMemory(id: Long) = viewModelScope.launch { memory.delete(id) }
    fun forgetAllMemory() = viewModelScope.launch { memory.deleteAll() }
    fun clearAudit() = viewModelScope.launch { audit.clear() }

    fun saveBrain(key: String, model: String) = viewModelScope.launch { store.saveBrain(key.trim(), model.trim()) }
    fun setLocale(v: String) = viewModelScope.launch { store.setLocale(v) }
    fun setSpeak(v: Boolean) = viewModelScope.launch { store.setSpeak(v) }
    fun setHandsFree(v: Boolean) = viewModelScope.launch { store.setHandsFree(v) }
    fun setBarge(v: Boolean) = viewModelScope.launch { store.setBarge(v) }
    fun setOnDevice(v: Boolean) = viewModelScope.launch { store.setOnDevice(v) }
    fun setMemoryMaster(v: Boolean) = viewModelScope.launch { store.setMemory(v) }

    fun wipeEverything() = viewModelScope.launch {
        session.newThread()
        tasks.deleteAll(); notes.deleteAll(); memory.deleteAll(); audit.clear(); conversations.clearAll()
    }
}
