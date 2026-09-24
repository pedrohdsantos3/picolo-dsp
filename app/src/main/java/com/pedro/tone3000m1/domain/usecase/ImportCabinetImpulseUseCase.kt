package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.CabinetImpulseEngine
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.CabinetImpulseRepository

internal class ImportCabinetImpulseUseCase(
    private val downloads: DownloadToneModelUseCase,
    private val prepareImpulseResponse: PrepareImpulseResponseUseCase,
    private val repository: CabinetImpulseRepository,
    private val engine: CabinetImpulseEngine,
) {
    fun execute(toneId: String, title: String, imageUrl: String, model: OnlineModel, token: String, moduleType: String): String {
        val downloaded = downloads.execute(model, token, "cabinet-${model.id}.wav")
        var normalized = downloaded
        var loaded = false
        try {
            normalized = prepareImpulseResponse.execute(downloaded)
            val result = engine.loadImpulseResponse(normalized.absolutePath)
            check(result.startsWith("IR LOADED")) { result }
            loaded = true

            val position = engine.namBlockCount()
            val mix = if (moduleType == "FX") 0.5f else 1.0f
            engine.setPosition(position)
            engine.setBypass(false)
            engine.setInGain(0.0f)
            engine.setOutGain(0.0f)
            engine.setMix(mix)
            engine.setEqPre(false)
            engine.setEqEnabled(true)
            for (band in 0 until 6) engine.setEqDb(band, 0.0f)
            repository.save(normalized, imageUrl, title, toneId, moduleType, position, mix)
            val audio = engine.start()
            if (downloaded != normalized) downloaded.delete()
            return audio
        } catch (error: Exception) {
            if (!loaded) {
                downloaded.delete()
                if (normalized != downloaded) normalized.delete()
            }
            throw error
        }
    }
}
