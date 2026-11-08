package com.z.takatuf.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Unsent form text kept on the device, so an interrupted connection or a closed app never
 * costs a student what they typed (US21, NFR06). Drafts are stored per form and cleared once
 * the form is sent. Supported forms: new listings, applications, team updates, contributions,
 * feedback and reports.
 */
class DraftStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    fun load(key: String): Map<String, String>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.optString(it) }.takeIf { values -> values.values.any { it.isNotBlank() } }
        } catch (_: Exception) {
            null
        }
    }

    fun save(key: String, values: Map<String, String>) {
        if (values.values.all { it.isBlank() }) {
            clear(key)
            return
        }
        prefs.edit { putString(key, JSONObject(values).toString()) }
    }

    fun clear(key: String) {
        prefs.edit { remove(key) }
    }

    /** On sign-out, so the next person on this phone never sees someone else's unsent text. */
    fun clearAll() {
        prefs.edit(commit = true) { clear() }
    }

    companion object {
        fun listing(kind: String, listingId: String?) = "editor_${listingId ?: "new_$kind"}"
        fun application(listingId: String) = "apply_$listingId"
        fun update(listingId: String) = "update_$listingId"
        fun contribution(listingId: String, uid: String) = "contribution_${listingId}_$uid"
        fun feedback(listingId: String, subjectUid: String) = "feedback_${listingId}_$subjectUid"
        fun report(targetKey: String) = "report_$targetKey"
    }
}
