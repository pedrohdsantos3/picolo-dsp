package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.LocalNamImportController
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.ImportedNamFile
import com.pedro.tone3000m1.domain.repository.ToneImportRepository
import com.pedro.tone3000m1.domain.usecase.ImportLocalNamFileUseCase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalNamImportControllerTest {
    @Test fun importsIntoRequestedSlotAndRebuildsUpdatedChain() {
        val first = entry("first.nam")
        val entries = mutableListOf(first)
        var rebuilt: List<ExtraNamEntry>? = null
        val imported = File.createTempFile("local-import", ".nam")
        val controller = controller(imported, entries, rebuild = { rebuilt = it; "NAM CHAIN READY" })

        val result = controller.import("Local", "replacement.nam", "encoded", "nam-0")

        assertEquals(LocalNamImportController.Result.Success("nam-0"), result)
        assertEquals(1, entries.size)
        assertEquals(imported.absolutePath, entries.single().path)
        assertEquals(entries, rebuilt)
        imported.delete()
    }

    @Test fun rollsBackChainAndDeletesFileWhenNativeRebuildFails() {
        val original = entry("original.nam")
        val entries = mutableListOf(original)
        val imported = File.createTempFile("local-import-fail", ".nam")
        val rebuilt = mutableListOf<List<ExtraNamEntry>>()
        val controller = controller(imported, entries, rebuild = {
            rebuilt += it
            if (it.singleOrNull()?.path == imported.absolutePath) "NAM LOAD FAILED" else "NAM CHAIN READY"
        })

        val result = controller.import("Local", "replacement.nam", "encoded", "nam-0")

        assertEquals(LocalNamImportController.Result.Failure("NAM LOAD FAILED"), result)
        assertEquals(listOf(original), entries)
        assertEquals(2, rebuilt.size)
        assertEquals(imported.absolutePath, rebuilt.first().single().path)
        assertEquals(listOf(original), rebuilt.last())
        assertFalse(imported.exists())
    }

    private fun controller(
        file: File,
        entries: MutableList<ExtraNamEntry>,
        rebuild: (List<ExtraNamEntry>) -> String,
    ) = LocalNamImportController(
        importFile = ImportLocalNamFileUseCase(object : ToneImportRepository {
            override fun writeLocalNam(originalName: String, encodedData: String) = ImportedNamFile(originalName, file)
            override fun preparePendingDownload(fileName: String) = error("unused")
            override fun normalizeImpulseResponseWav(source: File) = error("unused")
            override fun commitCurrentModel(pendingFile: File) = error("unused")
            override fun commitExtraModel(pendingFile: File, modelId: Long) = error("unused")
        }),
        readEntries = { entries.toList() },
        persistEntries = { entries.clear(); entries.addAll(it) },
        rebuildChain = rebuild,
        maxEntries = 8,
    )

    private fun entry(path: String) = ExtraNamEntry(
        toneId = path,
        toneTitle = path,
        modelId = 1L,
        modelName = path,
        size = "unknown",
        path = path,
        bypass = false,
    )
}
