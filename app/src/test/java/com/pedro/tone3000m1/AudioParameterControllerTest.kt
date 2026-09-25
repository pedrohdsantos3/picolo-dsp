package com.pedro.tone3000m1

import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import com.pedro.tone3000m1.controller.AudioParameterController
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioParameterControllerTest {
    @Test
    fun setInputGainClampsValueThenAppliesAndPersistsIt() {
        var nativeGain: Float? = null
        val savedFloats = mutableMapOf<String, Float>()
        val controller = controller(
            saveFloat = { key, value -> savedFloats[key] = value },
            setInputGain = { nativeGain = it },
        )

        controller.setInputGain(30.0)

        assertEquals(24.0f, nativeGain!!, 0.0f)
        assertEquals(24.0f, savedFloats[PresetPreferenceKeys.INPUT_GAIN]!!, 0.0f)
    }

    @Test
    fun setGateThresholdAndEqEnabledUseTheirPersistedKeys() {
        var nativeThreshold: Float? = null
        var nativeEqEnabled: Boolean? = null
        val savedFloats = mutableMapOf<String, Float>()
        val savedBooleans = mutableMapOf<String, Boolean>()
        val controller = controller(
            saveFloat = { key, value -> savedFloats[key] = value },
            saveBoolean = { key, value -> savedBooleans[key] = value },
            setGateThreshold = { nativeThreshold = it },
            setEqEnabled = { nativeEqEnabled = it },
        )

        controller.setGateThreshold(-100.0)
        controller.setEqEnabled(false)

        assertEquals(-90.0f, nativeThreshold!!, 0.0f)
        assertEquals(-90.0f, savedFloats[PresetPreferenceKeys.GATE_THRESHOLD]!!, 0.0f)
        assertEquals(false, nativeEqEnabled)
        assertEquals(false, savedBooleans[PresetPreferenceKeys.EQ_ENABLED])
    }

    private fun controller(
        saveFloat: (String, Float) -> Unit = { _, _ -> },
        saveBoolean: (String, Boolean) -> Unit = { _, _ -> },
        setInputGain: (Float) -> Unit = {},
        setOutputGain: (Float) -> Unit = {},
        setGateEnabled: (Boolean) -> Unit = {},
        setGateThreshold: (Float) -> Unit = {},
        setEqLow: (Float) -> Unit = {},
        setEqMid: (Float) -> Unit = {},
        setEqHigh: (Float) -> Unit = {},
        setEqEnabled: (Boolean) -> Unit = {},
    ) = AudioParameterController(
        saveFloatPreference = saveFloat,
        saveBooleanPreference = saveBoolean,
        setInputGainNative = setInputGain,
        setOutputGainNative = setOutputGain,
        setGateEnabledNative = setGateEnabled,
        setGateThresholdNative = setGateThreshold,
        setEqLowNative = setEqLow,
        setEqMidNative = setEqMid,
        setEqHighNative = setEqHigh,
        setEqEnabledNative = setEqEnabled,
    )
}
