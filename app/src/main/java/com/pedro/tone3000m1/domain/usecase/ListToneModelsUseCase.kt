package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.Tone3000Repository

internal class ListToneModelsUseCase(
    private val tones: Tone3000Repository,
) {
    fun execute(toneId: String, token: String, architecture: Int? = 2): List<OnlineModel> =
        tones.listModels(toneId, token, architecture)
}
