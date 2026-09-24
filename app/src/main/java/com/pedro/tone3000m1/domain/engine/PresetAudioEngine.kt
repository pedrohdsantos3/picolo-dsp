package com.pedro.tone3000m1.domain.engine

internal interface PresetAudioEngine {
    fun switchPresetGapless(
        path: String,
        inputGainDb: Float,
        outputGainDb: Float,
        inputChannel: Int,
        outputPair: Int,
        gateEnabled: Boolean,
        gateThresholdDb: Float,
        eqLowDb: Float,
        eqMidDb: Float,
        eqHighDb: Float,
    ): String
}
