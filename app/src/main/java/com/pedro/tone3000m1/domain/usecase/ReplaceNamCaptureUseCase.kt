package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.engine.NamChainRebuildEngine
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.OnlineModel
import java.io.File

internal data class ReplacedNamCapture(
    val entries: List<ExtraNamEntry>,
    val previousPath: String,
    val replacement: ExtraNamEntry,
    val rebuildResult: String,
    val audioResult: String,
)

internal class ReplaceNamCaptureUseCase(
    private val commitModel: CommitExtraToneModelUseCase,
    private val prepareReplacement: PrepareNamBlockReplacementUseCase,
    private val rebuild: RebuildNamChainUseCase,
    private val engine: NamChainRebuildEngine,
) {
    fun execute(
        currentEntries: List<ExtraNamEntry>,
        replacementIndex: Int,
        pendingFile: File,
        toneId: String,
        toneTitle: String,
        imageUrl: String,
        model: OnlineModel,
        moduleType: String,
    ): ReplacedNamCapture {
        require(replacementIndex in currentEntries.indices) {
            "NAM block ${replacementIndex + 1} no longer exists."
        }
        val wasRunning = engine.isRunning()
        val committed = commitModel.execute(pendingFile, model.id)
        val updated = currentEntries.toMutableList()
        val previous = updated[replacementIndex]
        val replacement = prepareReplacement.execute(
            previous = previous,
            toneId = toneId,
            toneTitle = toneTitle,
            model = model,
            path = committed.absolutePath,
            imageUrl = imageUrl,
            moduleType = moduleType,
        )
        updated[replacementIndex] = replacement
        val rebuildResult = try {
            rebuild.execute(updated)
        } catch (error: Exception) {
            rollback(currentEntries, wasRunning)
            committed.delete()
            throw error
        }
        if (!rebuildResult.startsWith(NAM_CHAIN_READY_PREFIX)) {
            val rollbackResult = rollback(currentEntries, wasRunning)
            committed.delete()
            error(
                if (rollbackResult.startsWith(NAM_CHAIN_READY_PREFIX)) rebuildResult
                else "$rebuildResult\nPrevious NAM chain restore failed: $rollbackResult",
            )
        }
        // RebuildNamChainUseCase already restarts audio when it was running.
        // Starting again here closes and reopens the audio stream a second time.
        val audioResult = if (engine.isRunning()) "AUDIO ACTIVE" else engine.startChain()
        if (!audioResult.startsWith(AUDIO_ACTIVE_PREFIX)) {
            val rollbackResult = rollback(currentEntries, wasRunning)
            committed.delete()
            error(
                if (rollbackResult.startsWith(NAM_CHAIN_READY_PREFIX)) audioResult
                else "$audioResult\nPrevious NAM chain restore failed: $rollbackResult",
            )
        }
        return ReplacedNamCapture(updated, previous.path, replacement, rebuildResult, audioResult)
    }

    private fun rollback(previousEntries: List<ExtraNamEntry>, wasRunning: Boolean): String {
        val restored = rebuild.execute(previousEntries)
        if (!restored.startsWith(NAM_CHAIN_READY_PREFIX)) return restored
        if (wasRunning && !engine.isRunning()) {
            val audio = engine.startChain()
            if (!audio.startsWith(AUDIO_ACTIVE_PREFIX)) return "$restored\n$audio"
        }
        return restored
    }

    private companion object {
        const val NAM_CHAIN_READY_PREFIX = "NAM CHAIN READY"
        const val AUDIO_ACTIVE_PREFIX = "AUDIO ACTIVE"
    }
}
