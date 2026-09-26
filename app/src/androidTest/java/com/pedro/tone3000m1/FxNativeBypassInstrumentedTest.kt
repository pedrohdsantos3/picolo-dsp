package com.pedro.tone3000m1

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedro.tone3000m1.controller.FxParameterController
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FxNativeBypassInstrumentedTest {
    @Test
    fun nativeAudioDiagnosticsAreAvailableOnDevice() {
        val audioEngine = NativeAudioEngine()

        assertFalse(audioEngine.nativeGetAudioDeviceInfo().isBlank())
        assertFalse(audioEngine.nativeScanUsbAudio().isBlank())
    }

    @Test
    fun bypassUpdatePersistsStateAndCallsNativeEngineWithoutRecursion() {
        val audioEngine = NativeAudioEngine()
        audioEngine.nativeConfigureFxNative(slot = 0, type = 0, namBlocksBefore = 6)
        var entries = mutableListOf(FxNativeEntry(effect = 0, param1 = 350f, param2 = 0.35f))
        val controller = FxParameterController(
            readFxEntries = { mutableListOf<FxImpulseEntry>() },
            persistFxEntries = {},
            setFxBypassNative = { _, _ -> },
            setFxMixNative = { _, _ -> },
            readNativeEntries = { entries.toMutableList() },
            persistNativeEntries = { entries = it.toMutableList() },
            applyNativeBypass = audioEngine::nativeSetFxNativeBypass,
            applyNativeMix = audioEngine::nativeSetFxNativeMix,
            applyNativeParameter = audioEngine::nativeSetFxNativeParameter,
            isAudioRunning = { false },
            syncNativeChain = {},
            restartAudio = {},
        )

        try {
            controller.setNativeBypass(nativeIndex = 0, bypassed = true)

            assertEquals(true, entries.single().bypass)
            controller.setNativeBypass(nativeIndex = 0, bypassed = false)
            assertEquals(false, entries.single().bypass)
        } finally {
            audioEngine.nativeClearFxNative(0)
        }
    }
}
