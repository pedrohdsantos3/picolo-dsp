package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.FxParameterController
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class FxParameterControllerTest {
    @Test
    fun nativeParameterIsClampedAndPersistedBeforeBeingApplied() {
        var entries = mutableListOf(nativeEntry())
        var nativeParameter: Triple<Int, Int, Float>? = null
        val controller = controller(
            readNative = { entries.toMutableList() },
            persistNative = { entries = it.toMutableList() },
            applyNativeParameter = { index, parameter, value ->
                nativeParameter = Triple(index, parameter, value)
            },
        )

        controller.setNativeParameter(nativeIndex = 0, parameter = 0, value = -10.0)

        assertEquals(Triple(0, 0, 20.0f), nativeParameter)
        assertEquals(20.0f, entries.single().param1, 0.0f)
    }

    @Test
    fun invalidParameterIndexDoesNotPersistOrCallNativeEngine() {
        var persistCount = 0
        var nativeCount = 0
        val controller = controller(
            persistNative = { persistCount++ },
            applyNativeParameter = { _, _, _ -> nativeCount++ },
        )

        controller.setNativeParameter(nativeIndex = 0, parameter = 3, value = 10.0)

        assertEquals(0, persistCount)
        assertEquals(0, nativeCount)
    }

    @Test
    fun nativeBypassIsPersistedAndAppliedOnceWithoutRecursion() {
        var entries = mutableListOf(nativeEntry())
        val nativeChanges = mutableListOf<Pair<Int, Boolean>>()
        val controller = controller(
            readNative = { entries.toMutableList() },
            persistNative = { entries = it.toMutableList() },
            applyNativeBypass = { index, bypassed -> nativeChanges += index to bypassed },
        )

        controller.setNativeBypass(nativeIndex = 0, bypassed = true)

        assertEquals(true, entries.single().bypass)
        assertEquals(listOf(0 to true), nativeChanges)
    }

    private fun controller(
        readFx: () -> MutableList<FxImpulseEntry> = { mutableListOf() },
        persistFx: (List<FxImpulseEntry>) -> Unit = {},
        setFxBypass: (Int, Boolean) -> Unit = { _, _ -> },
        setFxMix: (Int, Float) -> Unit = { _, _ -> },
        readNative: () -> MutableList<FxNativeEntry> = { mutableListOf(nativeEntry()) },
        persistNative: (List<FxNativeEntry>) -> Unit = {},
        applyNativeBypass: (Int, Boolean) -> Unit = { _, _ -> },
        applyNativeMix: (Int, Float) -> Unit = { _, _ -> },
        applyNativeParameter: (Int, Int, Float) -> Unit = { _, _, _ -> },
    ) = FxParameterController(
        readFxEntries = readFx,
        persistFxEntries = persistFx,
        setFxBypassNative = setFxBypass,
        setFxMixNative = setFxMix,
        readNativeEntries = readNative,
        persistNativeEntries = persistNative,
        applyNativeBypass = applyNativeBypass,
        applyNativeMix = applyNativeMix,
        applyNativeParameter = applyNativeParameter,
        isAudioRunning = { false },
        syncNativeChain = {},
        restartAudio = {},
    )

    private fun nativeEntry() = FxNativeEntry(effect = 0, param1 = 350f, param2 = 0.35f)
}
