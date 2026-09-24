package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.PrimaryToneCaptureEngine
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.ActiveToneRepository
import java.io.File

internal data class LoadedPrimaryToneCapture(
    val file: File,
    val loadResult: String,
    val audioResult: String,
)

/** Validates a downloaded capture in the native engine before committing it as the active model. */
internal class LoadPrimaryToneCaptureUseCase(
    private val commits: CommitCurrentToneModelUseCase,
    private val activeTone: ActiveToneRepository,
    private val engine: PrimaryToneCaptureEngine,
) {
    fun execute(
        toneId: String,
        toneTitle: String,
        model: OnlineModel,
        moduleType: String,
        downloadedFile: File,
    ): LoadedPrimaryToneCapture {
        val loadResult = engine.loadModel(downloadedFile.absolutePath)
        if (!loadResult.startsWith("MODEL LOADED")) {
            throw RuntimeException("NAM rejected the selected capture:\n$loadResult")
        }

        engine.applyModuleDefaults(moduleType)
        val audioResult = engine.start()
        val committed = commits.execute(downloadedFile)
        activeTone.save(toneId, toneTitle, model, moduleType, committed)
        return LoadedPrimaryToneCapture(committed, loadResult, audioResult)
    }
}
