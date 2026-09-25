package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.FxChainRemovalController
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxChainRemovalControllerTest {
    @Test
    fun removeReloadsAndPersistsRemainingEffectsBeforeResumingAudio() {
        val initial = mutableListOf(fx("/removed.wav"), fx("/remaining.wav"))
        var persisted: List<FxImpulseEntry>? = null
        val events = mutableListOf<String>()
        val controller = controller(
            entries = initial,
            persist = { persisted = it.toList(); events += "persist" },
            running = true,
            namCount = 2,
            restart = { events += "restart"; "AUDIO ACTIVE" },
            onLoad = { _, path -> events += "load:$path"; "FX LOADED" },
            status = events::add,
        )

        assertTrue(controller.remove(0))

        assertEquals(listOf("/remaining.wav"), persisted?.map { it.path })
        assertEquals(1, persisted?.single()?.position)
        assertEquals(listOf("load:/remaining.wav", "persist", "restart", "FX REMOVED\nAUDIO ACTIVE"), events)
    }

    @Test
    fun failedReloadDoesNotPersistOrDeleteRemovedFile() {
        var persistCount = 0
        val deleted = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val controller = controller(
            entries = mutableListOf(fx("/removed.wav"), fx("/remaining.wav")),
            persist = { persistCount++ },
            onLoad = { _, _ -> "ERROR: file missing" },
            delete = deleted::add,
            status = statuses::add,
        )

        assertFalse(controller.remove(0))

        assertEquals(0, persistCount)
        assertTrue(deleted.isEmpty())
        assertEquals(listOf("FX CHAIN RELOAD FAILED\nERROR: file missing"), statuses)
    }

    private fun controller(
        entries: MutableList<FxImpulseEntry>,
        persist: (List<FxImpulseEntry>) -> Unit = {},
        running: Boolean = false,
        namCount: Int = 0,
        restart: () -> String = { "" },
        onLoad: (Int, String) -> String = { _, _ -> "FX LOADED" },
        delete: (String) -> Unit = {},
        status: (String) -> Unit = {},
    ) = FxChainRemovalController(
        readEntries = { entries.toMutableList() },
        persistEntries = persist,
        isAudioRunning = { running },
        clearFxSlot = {},
        loadFxSlot = onLoad,
        setFxBypass = { _, _ -> },
        setFxMix = { _, _ -> },
        namBlockCount = { namCount },
        setFxPosition = { _, _ -> },
        cabinetPathExists = { false },
        deleteFile = delete,
        restartAudio = restart,
        publishStatus = status,
    )

    private fun fx(path: String) = FxImpulseEntry(path = path, position = 1)
}
