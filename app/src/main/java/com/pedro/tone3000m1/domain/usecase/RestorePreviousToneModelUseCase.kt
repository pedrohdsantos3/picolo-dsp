package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.ToneModelEngine
import com.pedro.tone3000m1.domain.repository.CurrentToneRepository

internal data class ToneModelRestoreResult(
    val restored: Boolean,
    val details: String,
)

/** Reloads the persisted model after a new model fails to load. */
internal class RestorePreviousToneModelUseCase(
    private val currentTone: CurrentToneRepository,
    private val engine: ToneModelEngine,
) {
    fun execute(): ToneModelRestoreResult {
        val file = currentTone.currentModelFile()
            ?: return ToneModelRestoreResult(false, "No persisted model path.")
        if (!file.exists()) return ToneModelRestoreResult(false, "Persisted model file does not exist.")

        return try {
            val result = engine.loadModel(file.absolutePath)
            ToneModelRestoreResult(result.startsWith("MODEL LOADED"), result)
        } catch (error: Exception) {
            ToneModelRestoreResult(false, error.message ?: error.toString())
        }
    }
}
