package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.Tone3000Repository
import com.pedro.tone3000m1.domain.repository.ToneImportRepository
import java.io.File

internal class DownloadToneModelUseCase(
    private val tones: Tone3000Repository,
    private val files: ToneImportRepository,
) {
    fun execute(model: OnlineModel, token: String, fileName: String): File {
        val destination = files.preparePendingDownload(fileName)
        return try {
            tones.downloadModel(model, token, destination)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }
}
