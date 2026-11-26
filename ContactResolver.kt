package com.maherma.app.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactMatch(val name: String, val number: String)

sealed interface ContactResult {
    data class Found(val match: ContactMatch) : ContactResult
    data class Multiple(val matches: List<ContactMatch>) : ContactResult
    data object NotFound : ContactResult
    data object NoPermission : ContactResult
}

@Singleton
class ContactResolver @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val numberRegex = Regex("""\+?\d[\d\s\-().]{5,}\d""")

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun resolve(query: String): ContactResult = withContext(Dispatchers.IO) {
        val q = query.trim()
        // A literal number (or "Name – +92 300 1234567" picked from a chip) needs no permission.
        numberRegex.find(q)?.let { m ->
            val name = q.replace(m.value, "").trim(' ', '–', '-', '(', ')').ifBlank { m.value.trim() }
            return@withContext ContactResult.Found(ContactMatch(name, m.value.trim()))
        }
        if (!hasPermission()) return@withContext ContactResult.NoPermission

        val rows = ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"),
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) add(ContactMatch(c.getString(0).orEmpty(), c.getString(1).orEmpty()))
            }
        }.orEmpty()

        val distinct = rows.distinctBy { PhoneNumberUtils.normalizeNumber(it.number) }
        val exact = distinct.filter { it.name.equals(q, ignoreCase = true) }
        val candidates = exact.ifEmpty { distinct }
        when (candidates.size) {
            0 -> ContactResult.NotFound
            1 -> ContactResult.Found(candidates[0])
            else -> ContactResult.Multiple(candidates.take(6))
        }
    }
}
