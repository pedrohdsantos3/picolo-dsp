package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry

internal data class NamBlockRemovalResult(
    val rebuildResult: String,
    val remainingBlockCount: Int,
)

/** Edits the full active NAM chain, including its primary model and additional blocks. */
internal class NamChainEditController(
    private val readEntries: () -> MutableList<ExtraNamEntry>,
    private val persistEntries: (List<ExtraNamEntry>) -> Unit,
    private val rebuildNativeChain: (List<ExtraNamEntry>) -> String,
) {
    fun moveBlock(chainIndex: Int, direction: Int): String? {
        val entries = readEntries()
        val targetIndex = chainIndex + direction
        if (chainIndex !in entries.indices || targetIndex !in entries.indices) return null

        val movedEntry = entries.removeAt(chainIndex)
        entries.add(targetIndex, movedEntry)
        val result = rebuildNativeChain(entries)
        if (result.startsWith(NAM_CHAIN_READY_PREFIX)) persistEntries(entries)
        return result
    }

    fun removeBlock(chainIndex: Int): NamBlockRemovalResult? {
        val entries = readEntries()
        if (chainIndex !in entries.indices) return null

        val removed = entries.removeAt(chainIndex)
        val result = rebuildNativeChain(entries)
        if (entries.isEmpty() || result.startsWith(NAM_CHAIN_READY_PREFIX)) {
            persistEntries(entries)
        }
        return NamBlockRemovalResult(result, entries.size)
    }

    private companion object {
        const val NAM_CHAIN_READY_PREFIX = "NAM CHAIN READY"
    }
}
