package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.OnlineModel

/** Updates capture metadata while preserving block controls unless its AMP/PEDAL role changes. */
internal class PrepareNamBlockReplacementUseCase {
    fun execute(
        previous: ExtraNamEntry,
        toneId: String,
        toneTitle: String,
        model: OnlineModel,
        path: String,
        imageUrl: String,
        moduleType: String,
    ): ExtraNamEntry {
        val changedModuleType = moduleType != previous.moduleType
        return previous.copy(
            toneId = toneId,
            toneTitle = toneTitle,
            modelId = model.id,
            modelName = model.name,
            size = model.size,
            path = path,
            imageUrl = imageUrl,
            moduleType = moduleType,
            a2Full = moduleType == "AMP",
            gainDb = if (changedModuleType) defaultGain(moduleType) else previous.gainDb,
            inGainDb = if (changedModuleType) 0f else previous.inGainDb,
            mix = if (changedModuleType) 1f else previous.mix,
            eqLowDb = if (changedModuleType) 0f else previous.eqLowDb,
            eqMidDb = if (changedModuleType) 0f else previous.eqMidDb,
            eqHighDb = if (changedModuleType) 0f else previous.eqHighDb,
            eqBand3Db = if (changedModuleType) 0f else previous.eqBand3Db,
            eqBand4Db = if (changedModuleType) 0f else previous.eqBand4Db,
            eqBand5Db = if (changedModuleType) 0f else previous.eqBand5Db,
            eqPre = if (changedModuleType) false else previous.eqPre,
            eqEnabled = if (changedModuleType) true else previous.eqEnabled,
            normalize = if (changedModuleType) moduleType != "PEDAL" else previous.normalize,
        )
    }

    private fun defaultGain(moduleType: String) = if (moduleType == "PEDAL") -10f else -15f
}
