package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.TonePackageCaptureRepository

/** Retains cached captures omitted by a partial API response while refreshing known entries. */
internal class MergePackageCapturesUseCase(
    private val captures: TonePackageCaptureRepository,
) {
    fun execute(toneId: String, moduleType: String, freshModels: List<OnlineModel>): List<OnlineModel> {
        val cachedModels = captures.read(toneId, moduleType)
        if (cachedModels.isEmpty()) return freshModels.distinctBy { it.id }

        val freshById = freshModels.associateBy { it.id }
        val merged = cachedModels.map { cached -> freshById[cached.id] ?: cached }
        val cachedIds = cachedModels.mapTo(mutableSetOf()) { it.id }
        return (merged + freshModels.filterNot { it.id in cachedIds }).distinctBy { it.id }
    }
}
