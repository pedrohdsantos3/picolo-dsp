package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.FxNativeChainController
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxNativeChainControllerTest {
    @Test
    fun addClampsEffectPersistsSynchronizesThenRestartsAudio() {
        var entries = mutableListOf<FxNativeEntry>()
        val events = mutableListOf<String>()
        val controller = controller(
            read = { entries.toMutableList() },
            persist = { entries = it.toMutableList(); events += "persist" },
            running = true,
            sync = { events += "sync:${it.size}" },
            restart = { events += "restart"; "AUDIO ACTIVE" },
            status = events::add,
        )

        assertTrue(controller.add(effect = 99))

        assertEquals(11, entries.single().effect)
        assertEquals(8f, entries.single().param1)
        assertEquals(0.8f, entries.single().param2)
        assertEquals(16000f, entries.single().param3)
        assertEquals(listOf("persist", "sync:1", "restart", "FXNATIVE ADDED\nStereo post NAM/CAB\nAUDIO ACTIVE"), events)
    }

    @Test
    fun addRejectsFullChainWithoutPersisting() {
        var persistCount = 0
        val statuses = mutableListOf<String>()
        val controller = controller(
            read = { MutableList(8) { nativeEntry() } },
            persist = { persistCount++ },
            status = statuses::add,
        )

        assertFalse(controller.add(effect = 0))

        assertEquals(0, persistCount)
        assertEquals(listOf("FXNATIVE CHAIN FULL\nMaximum 8 native effects."), statuses)
    }

    @Test
    fun removePersistsAndOnlyRestartsWhenOtherModulesRemain() {
        var entries = mutableListOf(nativeEntry(), nativeEntry())
        val events = mutableListOf<String>()
        val controller = controller(
            read = { entries.toMutableList() },
            persist = { entries = it.toMutableList(); events += "persist:${it.size}" },
            running = true,
            sync = { events += "sync:${it.size}" },
            hasOtherModules = true,
            restart = { events += "restart"; "AUDIO ACTIVE" },
            status = events::add,
        )

        assertTrue(controller.remove(nativeIndex = 0))

        assertEquals(1, entries.size)
        assertEquals(listOf("persist:1", "sync:1", "restart", "FXNATIVE REMOVED\nAUDIO ACTIVE"), events)
    }

    private fun controller(
        read: () -> MutableList<FxNativeEntry> = { mutableListOf() },
        persist: (List<FxNativeEntry>) -> Unit = {},
        running: Boolean = false,
        sync: (List<FxNativeEntry>) -> Unit = {},
        restart: () -> String = { "" },
        hasOtherModules: Boolean = false,
        status: (String) -> Unit = {},
    ) = FxNativeChainController(
        readEntries = read,
        persistEntries = persist,
        isAudioRunning = { running },
        syncNativeChain = sync,
        restartAudio = restart,
        hasOtherModules = { hasOtherModules },
        publishStatus = status,
    )

    private fun nativeEntry() = FxNativeEntry(effect = 0, param1 = 350f, param2 = 0.35f)
}
