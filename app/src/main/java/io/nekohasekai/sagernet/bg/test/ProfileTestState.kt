package io.nekohasekai.sagernet.bg.test

import io.nekohasekai.sagernet.bg.test.ProfileTestRunner.Phase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Transient card state. Run ownership prevents cancelled workers clearing a newer run. */
object ProfileTestState {
    data class State(val runId: Long, val phase: Phase? = null, val failure: Phase? = null)
    private val runs = AtomicLong()
    private val states = ConcurrentHashMap<Long, State>()
    val updates = MutableSharedFlow<Long>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun nextRunId() = runs.incrementAndGet()
    fun get(profileId: Long) = states[profileId]

    fun begin(profileId: Long, runId: Long) {
        states[profileId] = State(runId, Phase.URL)
        updates.tryEmit(profileId)
    }

    fun phase(profileId: Long, runId: Long, phase: Phase) {
        states.computeIfPresent(profileId) { _, state ->
            if (state.runId == runId) state.copy(phase = phase) else state
        }
        updates.tryEmit(profileId)
    }

    fun finish(profileId: Long, runId: Long, failure: Phase? = null) {
        states.computeIfPresent(profileId) { _, state ->
            if (state.runId != runId) state else failure?.let { State(runId, failure = it) }
        }
        updates.tryEmit(profileId)
    }

    fun clear() {
        states.clear()
        updates.tryEmit(-1L)
    }
}
