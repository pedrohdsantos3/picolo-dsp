package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.usecase.ImportCabinetImpulseUseCase
import com.pedro.tone3000m1.domain.usecase.ImportFxCaptureUseCase
import com.pedro.tone3000m1.data.repository.PendingImportModeRepository

/** Coordinates selected online capture imports without depending on Activity or UI classes. */
internal class OnlineCaptureImportController(
    private val importCabinet: ImportCabinetImpulseUseCase,
    private val importFx: ImportFxCaptureUseCase,
    private val pendingImportMode: PendingImportModeRepository,
    private val readFxCount: () -> Int,
    private val namBlockCount: () -> Int,
    private val cabinetIsLoaded: () -> Boolean,
    private val readCabinetPosition: (Int) -> Int,
    private val publishStatus: suspend (String) -> Unit,
    private val reportFailure: (String, Exception) -> Unit,
) {
    suspend fun loadSelected(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        model: OnlineModel,
        token: String,
        moduleType: String,
    ) {
        if (moduleType == FX_MODULE_TYPE) {
            loadFx(toneId, toneTitle, imageUrl, model, token)
        } else {
            loadCabinet(toneId, toneTitle, imageUrl, model, token, moduleType)
        }
    }

    private suspend fun loadCabinet(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        model: OnlineModel,
        token: String,
        moduleType: String,
    ) {
        val namCount = namBlockCount()
        val cabinetPosition = if (cabinetIsLoaded()) readCabinetPosition(namCount).coerceIn(0, namCount) else null
        try {
            val audio = importCabinet.execute(
                toneId = toneId,
                title = toneTitle,
                imageUrl = imageUrl,
                model = model,
                token = token,
                moduleType = moduleType,
                positionOverride = cabinetPosition,
            )
            pendingImportMode.clear()
            publishStatus("CABINET IR READY\n\n$toneTitle\n${model.name}\n$audio")
        } catch (error: Exception) {
            reportFailure("Cabinet IR download/load failed", error)
            publishStatus("CABINET IR LOAD FAILED\n\n${error.message}")
        }
    }

    private suspend fun loadFx(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        model: OnlineModel,
        token: String,
    ) {
        val importMode = pendingImportMode.read() ?: "add"
        val replacementIndex = importMode.removePrefix(FX_REPLACEMENT_PREFIX).toIntOrNull()
        if (readFxCount() >= MAX_FX_SLOTS && replacementIndex == null) {
            publishStatus("FX CHAIN FULL\nMaximum $MAX_FX_SLOTS space effects.")
            return
        }

        try {
            val imported = importFx.execute(
                toneId = toneId,
                toneTitle = toneTitle,
                imageUrl = imageUrl,
                model = model,
                token = token,
                requestedReplacementIndex = replacementIndex,
            )
            pendingImportMode.clear()
            publishStatus("FX READY\n\n$toneTitle\n${model.name}\n${imported.audioResult}")
        } catch (error: Exception) {
            reportFailure("FX import failed", error)
            publishStatus("FX LOAD FAILED\n${error.message}")
        }
    }

    private companion object {
        const val FX_MODULE_TYPE = "FX"
        const val FX_REPLACEMENT_PREFIX = "replace-fx:"
        const val MAX_FX_SLOTS = 8
    }
}
