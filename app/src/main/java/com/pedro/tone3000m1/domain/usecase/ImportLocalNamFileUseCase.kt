package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.ImportedNamFile
import com.pedro.tone3000m1.domain.repository.ToneImportRepository

internal class ImportLocalNamFileUseCase(
    private val files: ToneImportRepository,
) {
    fun execute(originalName: String, encodedData: String): ImportedNamFile {
        require(originalName.lowercase().endsWith(".nam")) {
            "Only .nam files are supported on Android"
        }
        require(encodedData.isNotBlank()) { "Empty local file" }
        return files.writeLocalNam(originalName, encodedData)
    }
}
