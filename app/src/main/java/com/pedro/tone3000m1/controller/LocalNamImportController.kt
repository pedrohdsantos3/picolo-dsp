package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.usecase.ImportLocalNamFileUseCase
import java.io.File

/** Coordinates local NAM file import with the active chain while leaving payload/UI encoding at the edge. */
internal class LocalNamImportController(
    private val importFile: ImportLocalNamFileUseCase,
    private val readEntries: () -> List<ExtraNamEntry>,
    private val persistEntries: (List<ExtraNamEntry>) -> Unit,
    private val rebuildChain: (List<ExtraNamEntry>) -> String,
    private val maxEntries: Int,
) {
    fun import(title: String, name: String, encodedData: String, targetBlockId: String): Result {
        var importedFile: File? = null
        return try {
            val imported = importFile.execute(name, encodedData)
            importedFile = imported.file
            val originalEntries = readEntries()
            val entries = originalEntries.toMutableList()
            val requested = targetBlockId.removePrefix("nam-").toIntOrNull()
            val target = requested?.takeIf { it in entries.indices } ?: entries.size
            if (target >= maxEntries) return Result.Failure("NAM chain full").also { imported.file.delete() }

            val entry = ExtraNamEntry(
                toneId = "local-${imported.file.name}",
                toneTitle = title,
                modelId = 0L,
                modelName = imported.originalName.removeSuffix(".nam"),
                size = "unknown",
                path = imported.file.absolutePath,
                bypass = false,
                eqLowDb = 0f,
                eqMidDb = 0f,
                eqHighDb = 0f,
                eqBand3Db = 0f,
                eqBand4Db = 0f,
                eqBand5Db = 0f,
            )
            if (target < entries.size) entries[target] = entry else entries.add(entry)
            persistEntries(entries)
            val rebuild = rebuildChain(entries)
            if (rebuild.contains("failed", ignoreCase = true) || rebuild.contains("error", ignoreCase = true)) {
                persistEntries(originalEntries)
                val restore = rebuildChain(originalEntries)
                imported.file.delete()
                val message =
                    if (restore.contains("failed", ignoreCase = true) || restore.contains("error", ignoreCase = true)) {
                        "$rebuild\nCould not restore the previous NAM chain: $restore"
                    } else {
                        rebuild
                    }
                Result.Failure(message)
            } else {
                Result.Success("nam-$target")
            }
        } catch (error: Exception) {
            importedFile?.delete()
            Result.Failure(error.message ?: "Local import failed")
        }
    }

    sealed interface Result {
        data class Success(val blockId: String) : Result
        data class Failure(val message: String) : Result
    }
}
