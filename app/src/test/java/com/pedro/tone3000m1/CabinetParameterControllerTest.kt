package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.CabinetParameterController
import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class CabinetParameterControllerTest {
    @Test
    fun setOutGainClampsAndPersistsTheAppliedValue() {
        var nativeValue: Float? = null
        val saved = mutableMapOf<String, Float>()
        val controller = controller(
            saveFloat = { key, value -> saved[key] = value },
            setOutGain = { nativeValue = it },
        )

        controller.setOutGain(20.0)

        assertEquals(12.0f, nativeValue!!, 0.0f)
        assertEquals(12.0f, saved[PresetPreferenceKeys.CABINET_IR_OUT_GAIN]!!, 0.0f)
    }

    @Test
    fun moveClampsTargetToCurrentNamChainSize() {
        var nativePosition: Int? = null
        var persistedPosition: Pair<String, Int>? = null
        val controller = controller(
            readPosition = { _, _ -> 2 },
            savePosition = { key, value -> persistedPosition = key to value },
            blockCount = { 2 },
            setPosition = { nativePosition = it },
        )

        controller.move(1)

        assertEquals(2, nativePosition)
        assertEquals(PresetPreferenceKeys.CABINET_IR_POSITION to 2, persistedPosition)
    }

    private fun controller(
        saveFloat: (String, Float) -> Unit = { _, _ -> },
        readPosition: (String, Int) -> Int = { _, default -> default },
        savePosition: (String, Int) -> Unit = { _, _ -> },
        blockCount: () -> Int = { 0 },
        setOutGain: (Float) -> Unit = {},
        setPosition: (Int) -> Unit = {},
    ) = CabinetParameterController(
        saveFloatPreference = saveFloat,
        saveBooleanPreference = { _, _ -> },
        readPositionPreference = readPosition,
        savePositionPreference = savePosition,
        namBlockCount = blockCount,
        setBypassNative = {},
        setPositionNative = setPosition,
        setInGainNative = {},
        setOutGainNative = setOutGain,
        setMixNative = {},
        setEqNative = { _, _ -> },
        setEqPositionNative = {},
        setEqEnabledNative = {},
        maxNamBlocks = 4,
    )
}
