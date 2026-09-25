package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.NamParameterController
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class NamParameterControllerTest {
    @Test
    fun setGainClampsAndPersistsTheSelectedBlockBeforeUpdatingNativeEngine() {
        var entries = mutableListOf(namEntry())
        var nativeValue: Pair<Int, Float>? = null
        var persistedEntry: ExtraNamEntry? = null
        val controller = controller(
            readEntries = { entries.toMutableList() },
            persistEntry = { index, updated ->
                persistedEntry = updated
                entries[index] = updated
            },
            setGain = { index, value -> nativeValue = index to value },
        )

        controller.setGain(chainIndex = 0, db = 20.0)

        assertEquals(0 to 12.0f, nativeValue)
        assertEquals(12.0f, persistedEntry!!.gainDb, 0.0f)
    }

    @Test
    fun setEqIgnoresBandOutsideNativeSixBandRange() {
        var nativeCalls = 0
        val controller = controller(setEq = { _, _, _ -> nativeCalls++ })

        controller.setEq(chainIndex = 0, band = 6, db = 4.0)

        assertEquals(0, nativeCalls)
    }

    @Test
    fun fullQualityIsRejectedForPedalModulesWithoutCallingNativeEngine() {
        val statuses = mutableListOf<String>()
        var nativeCalls = 0
        val controller = controller(
            readEntries = { mutableListOf(namEntry(moduleType = "PEDAL")) },
            setQuality = { _, _ -> nativeCalls++; "A2 ready" },
            publishStatus = statuses::add,
        )

        controller.setQuality(chainIndex = 0, full = true)

        assertEquals(0, nativeCalls)
        assertEquals(listOf("A2 Full is available for AMP blocks only."), statuses)
    }

    private fun controller(
        readEntries: () -> MutableList<ExtraNamEntry> = { mutableListOf(namEntry()) },
        persistEntry: (Int, ExtraNamEntry) -> Unit = { _, _ -> },
        setGain: (Int, Float) -> Unit = { _, _ -> },
        setEq: (Int, Int, Float) -> Unit = { _, _, _ -> },
        setQuality: (Int, Boolean) -> String = { _, _ -> "A2 ready" },
        publishStatus: (String) -> Unit = {},
    ) = NamParameterController(
        readEntries = readEntries,
        persistEntry = persistEntry,
        setBypassNative = { _, _ -> },
        setGainNative = setGain,
        setInGainNative = { _, _ -> },
        setMixNative = { _, _ -> },
        setEqNative = setEq,
        setEqPositionNative = { _, _ -> },
        setEqEnabledNative = { _, _ -> },
        setNormalizeNative = { _, _ -> },
        setQualityNative = setQuality,
        publishStatus = publishStatus,
    )

    private fun namEntry(moduleType: String = "AMP") = ExtraNamEntry(
        toneId = "tone-1",
        toneTitle = "Test tone",
        modelId = 1L,
        modelName = "Test capture",
        size = "small",
        path = "/tmp/test.nam",
        bypass = false,
        moduleType = moduleType,
    )
}
