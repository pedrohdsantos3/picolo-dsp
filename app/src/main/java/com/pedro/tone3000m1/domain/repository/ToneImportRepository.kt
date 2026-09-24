package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.ImportedNamFile
import java.io.File

internal interface ToneImportRepository {
    fun writeLocalNam(originalName: String, encodedData: String): ImportedNamFile
    fun preparePendingDownload(fileName: String): File
    fun normalizeImpulseResponseWav(source: File): File
    fun commitCurrentModel(pendingFile: File): File
    fun commitExtraModel(pendingFile: File, modelId: Long): File
}
