package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.repository.ToneImportRepository
import java.io.File

internal class PrepareImpulseResponseUseCase(
    private val files: ToneImportRepository,
) {
    fun execute(source: File): File = files.normalizeImpulseResponseWav(source)
}
