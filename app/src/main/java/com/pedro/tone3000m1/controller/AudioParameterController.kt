package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys

/** Applies global audio control limits and forwards values to persistence and the audio engine. */
internal class AudioParameterController(
    private val saveFloatPreference: (String, Float) -> Unit,
    private val saveBooleanPreference: (String, Boolean) -> Unit,
    private val setInputGainNative: (Float) -> Unit,
    private val setOutputGainNative: (Float) -> Unit,
    private val setGateEnabledNative: (Boolean) -> Unit,
    private val setGateThresholdNative: (Float) -> Unit,
    private val setEqLowNative: (Float) -> Unit,
    private val setEqMidNative: (Float) -> Unit,
    private val setEqHighNative: (Float) -> Unit,
    private val setEqEnabledNative: (Boolean) -> Unit,
) {
    fun setInputGain(db: Double) {
        setAndSaveFloat(db, -24f, 24f, PresetPreferenceKeys.INPUT_GAIN, setInputGainNative)
    }

    fun setOutputGain(db: Double) {
        setAndSaveFloat(db, -24f, 12f, PresetPreferenceKeys.OUTPUT_GAIN, setOutputGainNative)
    }

    fun setGateEnabled(enabled: Boolean) {
        setGateEnabledNative(enabled)
        saveBooleanPreference(PresetPreferenceKeys.GATE_ENABLED, enabled)
    }

    fun setGateThreshold(db: Double) {
        setAndSaveFloat(db, -90f, -20f, PresetPreferenceKeys.GATE_THRESHOLD, setGateThresholdNative)
    }

    fun setEqLow(db: Double) {
        setAndSaveFloat(db, -12f, 12f, PresetPreferenceKeys.EQ_LOW, setEqLowNative)
    }

    fun setEqMid(db: Double) {
        setAndSaveFloat(db, -12f, 12f, PresetPreferenceKeys.EQ_MID, setEqMidNative)
    }

    fun setEqHigh(db: Double) {
        setAndSaveFloat(db, -12f, 12f, PresetPreferenceKeys.EQ_HIGH, setEqHighNative)
    }

    fun setEqEnabled(enabled: Boolean) {
        setEqEnabledNative(enabled)
        saveBooleanPreference(PresetPreferenceKeys.EQ_ENABLED, enabled)
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
}
