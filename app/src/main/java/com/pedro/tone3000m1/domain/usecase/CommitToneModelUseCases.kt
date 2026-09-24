package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.repository.ToneImportRepository
import java.io.File

internal class CommitCurrentToneModelUseCase(
    private val files: ToneImportRepository,
) {
    fun execute(pendingFile: File): File = files.commitCurrentModel(pendingFile)
}

internal class CommitExtraToneModelUseCase(
    private val files: ToneImportRepository,
) {
    fun execute(pendingFile: File, modelId: Long): File = files.commitExtraModel(pendingFile, modelId)
}
