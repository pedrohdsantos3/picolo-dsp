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
        val rebuildResult = rebuild.execute(updated)
        check(rebuildResult.startsWith("NAM CHAIN READY")) { rebuildResult }
        val audioResult = engine.startChain()
        return ReplacedNamCapture(updated, previous.path, replacement, rebuildResult, audioResult)
    }
}
