package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.SignalChainReorderController
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalChainReorderControllerTest {
    @Test
    fun reordersFullNamChainAndPlacesFxRelativeToNamBlocks() {
        val originalNam = mutableListOf(nam("/primary.nam"), nam("/extra.nam"))
        var savedNam: List<ExtraNamEntry>? = null
        var savedFx: List<FxImpulseEntry>? = null
        var cabinetPosition: Int? = null
        var nativeFxPosition: Int? = null
        var restarted = false
        val statuses = mutableListOf<String>()
        val controller = controller(
            namEntries = originalNam,
            fxEntries = mutableListOf(fx("/space.wav")),
            running = true,
            persistNam = { savedNam = it.toList() },
            persistFx = { savedFx = it.toList() },
            saveCabinetPosition = { cabinetPosition = it },
            setFxPosition = { _, position -> nativeFxPosition = position },
            restart = { restarted = true; "AUDIO ACTIVE" },
            status = statuses::add,
        )

        val result = controller.reorder(listOf("nam-1", "fx-0", "nam-0", "cabinet-ir"))

        assertTrue(result)
        assertEquals(listOf("/extra.nam", "/primary.nam"), savedNam?.map { it.path })
        assertEquals(2, cabinetPosition)
        assertEquals(1, savedFx?.single()?.position)
        assertEquals(savedFx?.single()?.position, nativeFxPosition)
        assertTrue(restarted)
        assertEquals(listOf("NAM CHAIN READY\nblocks=2\nAUDIO ACTIVE"), statuses)
    }

    @Test
    fun failedNamRebuildRollsBackAndDoesNotPersistRequestedOrder() {
        var persistCount = 0
        var rebuildCalls = 0
        val statuses = mutableListOf<String>()
        val controller = controller(
            namEntries = mutableListOf(nam("/primary.nam"), nam("/extra.nam")),
            rebuild = {
                rebuildCalls++
                if (rebuildCalls == 1) "ERROR: target unavailable" else "NAM CHAIN READY\nblocks=2"
            },
            persistNam = { persistCount++ },
            status = statuses::add,
        )

        assertFalse(controller.reorder(listOf("nam-1", "nam-0")))

        assertEquals(2, rebuildCalls)
        assertEquals(0, persistCount)
        assertEquals(listOf("Reordenação cancelada: ERROR: target unavailable"), statuses)
    }

    private fun controller(
        namEntries: MutableList<ExtraNamEntry>,
        fxEntries: MutableList<FxImpulseEntry> = mutableListOf(),
        running: Boolean = false,
        rebuild: (List<ExtraNamEntry>) -> String = { "NAM CHAIN READY\nblocks=${it.size}" },
        persistNam: (List<ExtraNamEntry>) -> Unit = {},
        persistFx: (List<FxImpulseEntry>) -> Unit = {},
        saveCabinetPosition: (Int) -> Unit = {},
        setFxPosition: (Int, Int) -> Unit = { _, _ -> },
        restart: () -> String = { "" },
        status: (String) -> Unit = {},
    ) = SignalChainReorderController(
        readNamEntries = { namEntries.toMutableList() },
        persistNamEntries = persistNam,
        readFxEntries = { fxEntries.toMutableList() },
        persistFxEntries = persistFx,
        readCabinetPosition = { it },
        persistCabinetPosition = saveCabinetPosition,
        setCabinetPosition = {},
        isAudioRunning = { running },
        rebuildNamChain = rebuild,
        clearFxSlot = {},
        loadFxSlot = { _, _ -> "FX LOADED" },
        setFxBypass = { _, _ -> },
        setFxMix = { _, _ -> },
        setFxPosition = setFxPosition,
        hasModules = { true },
        restartAudio = restart,
        publishStatus = status,
        reportError = { throw it },
    )

    private fun nam(path: String) = ExtraNamEntry(
        toneId = path,
        toneTitle = path,
        modelId = 1L,
        modelName = path.substringAfterLast('/'),
        size = "small",
        path = path,
        bypass = false,
    )

    private fun fx(path: String) = FxImpulseEntry(path = path, position = 0)
}
