package com.maherma.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maherma.app.data.prefs.AppSettings
import com.maherma.app.data.prefs.SettingsStore
import com.maherma.app.data.repo.AuditRepository
import com.maherma.app.data.repo.ConversationRepository
import com.maherma.app.data.repo.TaskRepository
import com.maherma.app.voice.SessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantViewModel @Inject constructor(
    val session: SessionController,
    repo: ConversationRepository,
    tasks: TaskRepository,
    audit: AuditRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val keep = SharingStarted.WhileSubscribed(5_000)

    val items = session.threadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.messages(id) }
        .stateIn(viewModelScope, keep, emptyList())
    val threads = repo.threads().stateIn(viewModelScope, keep, emptyList())
    val openTasks = tasks.observeOpen().stateIn(viewModelScope, keep, emptyList())
    val recent = audit.observe().map { l -> l.filter { it.kind == "TOOL" }.take(3) }.stateIn(viewModelScope, keep, emptyList())
    val settings = settings.settings.stateIn(viewModelScope, keep, AppSettings())
}
