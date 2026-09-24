package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.ExtraNamChainEngine
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.ExtraNamChainRepository
import java.io.File

internal data class AddedExtraNamCapture(val entry: ExtraNamEntry, val chainResult: String, val audioResult: String)

internal class AddExtraNamCaptureUseCase(
    private val commitModel: CommitExtraToneModelUseCase,
    private val chain: ExtraNamChainRepository,
    private val engine: ExtraNamChainEngine,
) {
    fun execute(pendingFile: File, toneId: String, toneTitle: String, imageUrl: String, model: OnlineModel, moduleType: String): AddedExtraNamCapture {
        val committed = commitModel.execute(pendingFile, model.id)
        var addedToEngine = false
        try {
            val chainResult = engine.addChainModel(committed.absolutePath)
            check(chainResult.startsWith("CHAIN NAM ADDED")) { chainResult }
            addedToEngine = true

            val index = engine.namBlockCount() - 1
            val gainDb = if (moduleType == "PEDAL") -10.0f else -15.0f
            val normalize = moduleType != "PEDAL"
            val a2Full = moduleType == "AMP"
            engine.setBypass(index, false)
            engine.setInGain(index, 0.0f)
            engine.setMix(index, 1.0f)
            engine.setGain(index, gainDb)
            for (band in 0 until 6) engine.setEqDb(index, band, 0.0f)
            engine.setEqPre(index, false)
            engine.setNormalize(index, normalize)
            engine.setEqEnabled(index, true)
            engine.setQuality(index, a2Full)

            val entry = ExtraNamEntry(
                toneId = toneId,
                toneTitle = toneTitle,
                modelId = model.id,
                modelName = model.name,
                size = model.size,
                path = committed.absolutePath,
                bypass = false,
                gainDb = gainDb,
                normalize = normalize,
                imageUrl = imageUrl,
                moduleType = moduleType,
                a2Full = a2Full,
            )
            val audioResult = engine.start()
            chain.persistExtraNamChain(chain.readExtraNamChain().apply { add(entry) })
            return AddedExtraNamCapture(entry, chainResult, audioResult)
        } catch (error: Exception) {
            if (!addedToEngine) committed.delete()
            throw error
        }
    }
}
