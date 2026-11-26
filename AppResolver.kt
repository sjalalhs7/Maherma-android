package com.maherma.app.device

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppResolver @Inject constructor(@ApplicationContext private val ctx: Context) {
    data class App(val label: String, val pkg: String)

    sealed interface Result {
        data class Found(val app: App) : Result
        data class Multiple(val apps: List<App>) : Result
        data object None : Result
    }

    @Suppress("DEPRECATION")
    fun installed(): List<App> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun resolve(query: String): Result {
        val q = norm(query)
        if (q.isEmpty()) return Result.None
        val apps = installed()
        val tiers = listOf(
            apps.filter { norm(it.label) == q },
            apps.filter { norm(it.label).startsWith(q) },
            apps.filter { val l = norm(it.label); l.contains(q) || (l.length >= 3 && q.contains(l)) },
        )
        for (t in tiers) {
            if (t.size == 1) return Result.Found(t[0])
            if (t.size > 1) return Result.Multiple(t.take(4))
        }
        // Typo tolerance for spoken/typed names.
        val scored = apps.map { it to distance(norm(it.label), q) }.filter { it.second <= maxOf(1, q.length / 4) }
        if (scored.isEmpty()) return Result.None
        val best = scored.minOf { it.second }
        val top = scored.filter { it.second == best }.map { it.first }
        return if (top.size == 1) Result.Found(top[0]) else Result.Multiple(top.take(4))
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
