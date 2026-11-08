package com.z.takatuf.data

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.z.takatuf.data.ai.OpenAiRoles
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.RoleSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate

/**
 * The optional LLM service (charter, Technical Environment): the app sends a project description straight to
 * OpenAI and shows the roles it suggests, which the owner confirms before anything is used.
 *
 * The key comes from the project's `.env` at build time, so it is inside the APK. Give it a
 * spending cap in the OpenAI dashboard. Every recruitment feature keeps working without it
 * (NFR05): with no key the editor simply doesn't offer suggestions.
 */
class AiRepository(
    private val apiKey: String,
    private val model: String,
    private val dailyLimit: Int,
    private val prefs: SharedPreferences,
    private val endpoint: String = "https://api.openai.com/v1/responses",
) {
    val isAvailable: Boolean get() = apiKey.isNotBlank()

    suspend fun suggestRoles(
        kind: ListingKind,
        title: String,
        outcome: String,
        description: String,
        eventTitle: String?,
    ): List<RoleSuggestion> = withContext(Dispatchers.IO) {
        if (!isAvailable) throw AppException(AppError.AiUnavailable)
        val prompt = OpenAiRoles.prompt(kind, title, outcome, description, eventTitle)
        if ("$title $outcome $description".trim().length < OpenAiRoles.MIN_TEXT) return@withContext emptyList()
        if (usedToday() >= dailyLimit) throw AppException(AppError.AiLimit)

        val roles = OpenAiRoles.parse(post(OpenAiRoles.request(model, prompt).toString()))
        countUse()
        roles
    }

    private fun post(json: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Only the status: the request holds the student's description (NFR03).
                Log.w(TAG, "OpenAI request failed with HTTP $status")
                throw AppException(AppError.AiUnavailable)
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: UnknownHostException) {
            throw AppException(AppError.Offline, e)
        } catch (e: SocketTimeoutException) {
            throw AppException(AppError.AiUnavailable, e)
        } catch (e: IOException) {
            throw AppException(AppError.AiUnavailable, e)
        } finally {
            connection.disconnect()
        }
    }

    private fun usedToday(): Int =
        if (prefs.getString(KEY_DAY, null) == LocalDate.now().toString()) prefs.getInt(KEY_COUNT, 0) else 0

    private fun countUse() {
        val used = usedToday()
        prefs.edit {
            putString(KEY_DAY, LocalDate.now().toString())
            putInt(KEY_COUNT, used + 1)
        }
    }

    companion object {
        private const val TAG = "AiRepository"
        private const val KEY_DAY = "ai_day"
        private const val KEY_COUNT = "ai_count"
        const val PREFS = "ai_usage"
    }
}
