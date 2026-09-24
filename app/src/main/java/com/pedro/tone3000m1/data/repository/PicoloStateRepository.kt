package com.pedro.tone3000m1.data.repository

import com.pedro.tone3000m1.data.model.PicoloStateSnapshot
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Polls the existing Android/JNI state source away from the Compose layer. */
internal class PicoloStateRepository(
    private val readSnapshot: suspend () -> PicoloStateSnapshot,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    fun observe(): Flow<PicoloStateSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(readSnapshot())
            delay(intervalMs)
        }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 700L
    }
}
