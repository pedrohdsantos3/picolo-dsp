package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.model.NamEqDefaults

/** Replaces capture metadata and resets controls so the new NAM starts with its defaults. */
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
        val normalizedType = moduleType.uppercase()
        return previous.copy(
            toneId = toneId,
            toneTitle = toneTitle,
            modelId = model.id,
            modelName = model.name,
            size = model.size,
            path = path,
            imageUrl = imageUrl,
            bypass = false,
            moduleType = normalizedType,
            a2Full = false,
            gainDb = defaultGain(normalizedType),
            inGainDb = 0f,
            mix = 1f,
            eqLowDb = 0f,
            eqMidDb = 0f,
            eqHighDb = 0f,
            eqBand3Db = 0f,
            eqBand4Db = 0f,
            eqBand5Db = 0f,
            eqFrequenciesHz = NamEqDefaults.frequenciesHz,
            eqQValues = NamEqDefaults.qValues,
            eqTypes = NamEqDefaults.types,
            eqPre = false,
            eqEnabled = true,
            normalize = normalizedType != "PEDAL",
        )
    }

    private fun defaultGain(moduleType: String) = 0f
}
