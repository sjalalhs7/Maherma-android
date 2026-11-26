package com.maherma.app.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maherma.app.data.db.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms reminders after reboot or when the exact-alarm permission state changes. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { tasks.scheduled(System.currentTimeMillis()).forEach { scheduler.schedule(it) } } finally { pending.finish() }
        }
    }
}
