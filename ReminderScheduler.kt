package com.maherma.app.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.maherma.app.data.db.TaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val am get() = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    /** Schedules the reminder. Returns true when exact, false when it fell back to an approximate alarm. */
    fun schedule(task: TaskEntity): Boolean {
        val due = task.dueAt ?: return false
        val pi = pending(task.id)
        return try {
            if (canExact()) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi); true }
            else { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi); false }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi); false
        }
    }

    fun cancel(taskId: Long) { am.cancel(pending(taskId)) }

    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.toInt(),
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION = "com.maherma.app.REMINDER"
        const val EXTRA_ID = "task_id"
    }
}
