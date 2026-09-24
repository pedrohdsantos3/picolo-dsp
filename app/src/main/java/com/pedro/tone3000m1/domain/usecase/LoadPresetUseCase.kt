package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.PresetAudioEngine
import com.pedro.tone3000m1.domain.model.PresetData
import com.pedro.tone3000m1.domain.repository.PresetRepository

internal data class LoadedPreset(
    val preset: PresetData,
    val engineResult: String,
)

internal class LoadPresetUseCase(
    private val presets: PresetRepository,
    private val engine: PresetAudioEngine,
) {
    fun execute(preset: PresetData): LoadedPreset {
        val result = engine.switchPresetGapless(
            path = preset.modelPath,
            inputGainDb = preset.inputGainDb,
            outputGainDb = preset.outputGainDb,
            inputChannel = preset.inputChannel,
            outputPair = preset.outputPair,
            gateEnabled = preset.gateEnabled,
            gateThresholdDb = preset.gateThresholdDb,
            eqLowDb = preset.eqLowDb,
            eqMidDb = preset.eqMidDb,
            eqHighDb = preset.eqHighDb,
        )
        val accepted = result.startsWith("PRESET SWITCH QUEUED") ||
            result.startsWith("PRESET LOADED") ||
            result.startsWith("MODEL LOADED")
        check(accepted) { result }

        presets.activate(preset)
        return LoadedPreset(preset, result)
    }
}
