package com.pedro.tone3000m1.data.repository

import com.pedro.tone3000m1.data.model.PicoloStateSnapshot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive

/** Publishes application state on demand and samples only live audio metrics periodically. */
internal class PicoloStateRepository(
    private val readStats: suspend () -> String,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private val state = MutableStateFlow(PicoloStateSnapshot(pluginState = "{}", status = "", stats = ""))

    fun publish(pluginState: String, status: String) {
        state.update { it.copy(pluginState = pluginState, status = status) }
    }

    fun observe(): Flow<PicoloStateSnapshot> = combine(state.asStateFlow(), observeStats()) { snapshot, stats ->
        snapshot.copy(stats = stats)
    }

    private fun observeStats(): Flow<String> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readStats())
            delay(intervalMs)
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 700L
    }
}
