package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.NamChainEditController
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamChainEditControllerTest {
    @Test
    fun moveUsesFullChainIncludingPrimaryAndPersistsOnlyAfterRebuild() {
        var entries = mutableListOf(entry("/primary.nam"), entry("/extra.nam"))
        var persisted: List<ExtraNamEntry>? = null
        var rebuilt: List<ExtraNamEntry>? = null
        val controller = NamChainEditController(
            readEntries = { entries.toMutableList() },
            persistEntries = { persisted = it.toList() },
            rebuildNativeChain = { rebuilt = it.toList(); "NAM CHAIN READY\nblocks=${it.size}" },
        )

        val result = controller.moveBlock(chainIndex = 0, direction = 1)

        assertEquals("NAM CHAIN READY\nblocks=2", result)
        assertEquals(listOf("/extra.nam", "/primary.nam"), rebuilt?.map { it.path })
        assertEquals(rebuilt?.map { it.path }, persisted?.map { it.path })
    }

    @Test
    fun failedMoveDoesNotPersistTheRequestedOrder() {
        val original = mutableListOf(entry("/primary.nam"), entry("/extra.nam"))
        var persistCount = 0
        val controller = NamChainEditController(
            readEntries = { original.toMutableList() },
            persistEntries = { persistCount++ },
            rebuildNativeChain = { "ERROR: model unavailable" },
        )

        assertEquals("ERROR: model unavailable", controller.moveBlock(0, 1))
        assertEquals(0, persistCount)
        assertNull(controller.moveBlock(0, -1))
    }

    @Test
    fun removingPrimaryPersistsFullRemainingChainAndDeletesItsFile() {
        val primaryFile = Files.createTempFile("primary-nam", ".nam").toFile()
        var entries = mutableListOf(entry(primaryFile.absolutePath), entry("/extra.nam"))
        var persisted: List<ExtraNamEntry>? = null
        val controller = NamChainEditController(
            readEntries = { entries.toMutableList() },
            persistEntries = { persisted = it.toList() },
            rebuildNativeChain = { entries = it.toMutableList(); "NAM CHAIN READY\nblocks=${it.size}" },
        )

        val result = controller.removeBlock(chainIndex = 0)

        assertEquals("NAM CHAIN READY\nblocks=1", result?.rebuildResult)
        assertEquals(1, result?.remainingBlockCount)
        assertEquals(listOf("/extra.nam"), persisted?.map { it.path })
        assertFalse(primaryFile.exists())
    }

    @Test
    fun removingLastBlockPersistsEmptyChainEvenIfRebuildReportsFailure() {
        var persisted: List<ExtraNamEntry>? = null
        val controller = NamChainEditController(
            readEntries = { mutableListOf(entry("/only.nam")) },
            persistEntries = { persisted = it.toList() },
            rebuildNativeChain = { "ERROR: audio restart failed" },
        )

        val result = controller.removeBlock(chainIndex = 0)

        assertEquals(0, result?.remainingBlockCount)
        assertEquals(emptyList<ExtraNamEntry>(), persisted)
    }

    private fun entry(path: String) = ExtraNamEntry(
        toneId = path,
        toneTitle = path,
        modelId = 1L,
        modelName = path.substringAfterLast('/'),
        size = "small",
        path = path,
        bypass = false,
    )
}
