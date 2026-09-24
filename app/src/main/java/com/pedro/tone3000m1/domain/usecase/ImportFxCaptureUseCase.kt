package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.FxImpulseEngine
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.FxEffectsRepository
import java.io.File

internal data class ImportedFxCapture(
    val entry: FxImpulseEntry,
    val audioResult: String,
    val replacedPath: String?,
)

/** Downloads, normalizes, loads, and persists a file based FX capture. */
internal class ImportFxCaptureUseCase(
    private val downloads: DownloadToneModelUseCase,
    private val prepareImpulseResponse: PrepareImpulseResponseUseCase,
    private val effects: FxEffectsRepository,
    private val engine: FxImpulseEngine,
) {
    fun execute(
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        model: OnlineModel,
        token: String,
        requestedReplacementIndex: Int? = null,
    ): ImportedFxCapture {
        val entries = effects.readImpulseChain()
        val targetIndex = requestedReplacementIndex?.takeIf { it in entries.indices }
        check(entries.size < MAX_FX_EFFECTS || targetIndex != null) {
            "FX chain full (maximum $MAX_FX_EFFECTS)"
        }
        val slot = targetIndex ?: entries.size
        val replacedPath = targetIndex?.let { entries[it].path }
        var downloaded: File? = null
        var normalized: File? = null
        var chainPersisted = false
        try {
            downloaded = downloads.execute(model, token, "fx-space-${model.id}.wav")
            normalized = prepareImpulseResponse.execute(downloaded)
            val loaded = engine.loadImpulseResponse(slot, normalized.absolutePath)
            check(loaded.startsWith("FX LOADED")) { loaded }

            val entry = FxImpulseEntry(
                toneId = toneId,
                title = toneTitle,
                image = imageUrl,
                modelId = model.id,
                modelName = model.name,
                path = normalized.absolutePath,
                bypass = false,
                mix = 0.5f,
                position = engine.namBlockCount(),
            )
            if (targetIndex == null) entries.add(entry) else entries[targetIndex] = entry
            effects.persistImpulseChain(entries)
            chainPersisted = true
            engine.setImpulseResponseBypass(slot, entry.bypass)
            engine.setImpulseResponseMix(slot, entry.mix)
            engine.setImpulseResponsePosition(slot, entry.position)

            val audioResult = engine.start()
            if (!replacedPath.isNullOrBlank() && replacedPath != normalized.absolutePath) {
                File(replacedPath).delete()
            }
            if (downloaded != normalized) downloaded.delete()
            return ImportedFxCapture(entry, audioResult, replacedPath)
        } catch (error: Exception) {
            // Keep a file once the chain points at it; native audio may already
            // have loaded it even if a later control call fails.
            if (!chainPersisted) {
                downloaded?.delete()
                if (normalized != downloaded) normalized?.delete()
            }
            throw error
        }
    }

    private companion object {
        const val MAX_FX_EFFECTS = 8
    }
}
