package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.repository.PresetRepository

internal class SavePresetUseCase(
    private val presets: PresetRepository,
) {
    fun execute(slot: Int, namBypassFallback: Boolean): String =
        presets.saveCurrent(slot, namBypassFallback)
}
