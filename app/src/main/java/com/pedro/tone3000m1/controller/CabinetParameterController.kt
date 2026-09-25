package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys

/** Applies cabinet IR controls without depending on Activity lifecycle or UI elements. */
internal class CabinetParameterController(
    private val saveFloatPreference: (String, Float) -> Unit,
    private val saveBooleanPreference: (String, Boolean) -> Unit,
    private val readPositionPreference: (String, Int) -> Int,
    private val savePositionPreference: (String, Int) -> Unit,
    private val namBlockCount: () -> Int,
    private val setBypassNative: (Boolean) -> Unit,
    private val setPositionNative: (Int) -> Unit,
    private val setInGainNative: (Float) -> Unit,
    private val setOutGainNative: (Float) -> Unit,
    private val setMixNative: (Float) -> Unit,
    private val setEqNative: (Int, Float) -> Unit,
    private val setEqPositionNative: (Boolean) -> Unit,
    private val setEqEnabledNative: (Boolean) -> Unit,
    private val maxNamBlocks: Int,
) {
    fun setBypass(bypassed: Boolean) {
        setBypassNative(bypassed)
        saveBooleanPreference(PresetPreferenceKeys.CABINET_IR_BYPASS, bypassed)
    }

    fun move(direction: Int) {
        val maxPosition = namBlockCount().coerceIn(0, maxNamBlocks)
        val current = readPositionPreference(PresetPreferenceKeys.CABINET_IR_POSITION, maxPosition)
            .coerceIn(0, maxPosition)
        val next = (current + direction.coerceIn(-1, 1)).coerceIn(0, maxPosition)
        setPositionNative(next)
        savePositionPreference(PresetPreferenceKeys.CABINET_IR_POSITION, next)
    }

    fun setInGain(db: Double) {
        setAndSaveFloat(db, -24f, 24f, PresetPreferenceKeys.CABINET_IR_IN_GAIN, setInGainNative)
    }

    fun setOutGain(db: Double) {
        setAndSaveFloat(db, -24f, 12f, PresetPreferenceKeys.CABINET_IR_OUT_GAIN, setOutGainNative)
    }

    fun setMix(mix: Double) {
        setAndSaveFloat(mix, 0f, 1f, PresetPreferenceKeys.CABINET_IR_MIX, setMixNative)
    }

    fun setEq(band: Int, db: Double) {
        if (band !in EQ_BAND_INDICES) return
        val value = db.toFloat().coerceIn(-12f, 12f)
        setEqNative(band, value)
        saveFloatPreference(PresetPreferenceKeys.CABINET_IR_EQ_PREFIX + band, value)
    }

    fun setEqPosition(pre: Boolean) {
        setEqPositionNative(pre)
        saveBooleanPreference(PresetPreferenceKeys.CABINET_IR_EQ_PRE, pre)
    }

    fun setEqEnabled(enabled: Boolean) {
        setEqEnabledNative(enabled)
        saveBooleanPreference(PresetPreferenceKeys.CABINET_IR_EQ_ENABLED, enabled)
    }

    private fun setAndSaveFloat(
        rawValue: Double,
        min: Float,
        max: Float,
        preferenceKey: String,
        setNative: (Float) -> Unit,
    ) {
        val value = rawValue.toFloat().coerceIn(min, max)
        setNative(value)
        saveFloatPreference(preferenceKey, value)
    }

    private companion object {
        val EQ_BAND_INDICES = 0 until 6
    }
}
