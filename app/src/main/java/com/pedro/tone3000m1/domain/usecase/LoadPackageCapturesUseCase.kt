package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.TonePackageCaptureRepository
import java.util.Locale

/** Loads the API captures, retains cached entries omitted by partial responses, and refreshes the cache. */
internal class LoadPackageCapturesUseCase(
    private val listToneModels: ListToneModelsUseCase,
    private val mergePackageCaptures: MergePackageCapturesUseCase,
    private val cache: TonePackageCaptureRepository,
) {
    fun execute(toneId: String, moduleType: String, token: String): List<OnlineModel> {
        val normalizedType = moduleType.uppercase(Locale.ROOT)
        val architecture = if (normalizedType == "FX" || normalizedType == "IR") null else 2
        val freshModels = listToneModels.execute(toneId, token, architecture)
        val models = mergePackageCaptures.execute(toneId, moduleType, freshModels)
        if (models.isNotEmpty()) cache.save(toneId, moduleType, models)
        return models
    }
}
