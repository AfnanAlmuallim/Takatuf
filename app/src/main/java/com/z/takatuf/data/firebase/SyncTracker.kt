package com.z.takatuf.data.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Writes saved on this device that the server hasn't confirmed yet. The app shows how many
 * are waiting, says when they've all gone through, and reports any the server refused, so
 * pending, completed and failed actions are never confused (US21, NFR06).
 */
object SyncTracker {
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _outcomes = MutableSharedFlow<Outcome>(extraBufferCapacity = 8)
    val outcomes: SharedFlow<Outcome> = _outcomes.asSharedFlow()

    enum class Outcome { AllSent, Refused }

    fun track(task: Task<*>) {
        _pending.update { it + 1 }
        task.addOnCompleteListener { done ->
            val left = _pending.updateAndGet { (it - 1).coerceAtLeast(0) }
            when {
                !done.isSuccessful -> _outcomes.tryEmit(Outcome.Refused)
                left == 0 -> _outcomes.tryEmit(Outcome.AllSent)
            }
        }
    }

    private inline fun MutableStateFlow<Int>.updateAndGet(transform: (Int) -> Int): Int {
        while (true) {
            val current = value
            val next = transform(current)
            if (compareAndSet(current, next)) return next
        }
    }
}
