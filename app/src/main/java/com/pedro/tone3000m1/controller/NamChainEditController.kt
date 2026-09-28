package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry

internal data class NamBlockRemovalResult(
    val rebuildResult: String,
    val remainingBlockCount: Int,
)

/** Edits the full active NAM chain, including its primary model and additional blocks. */
internal class NamChainEditController(
    private val readEntries: () -> MutableList<ExtraNamEntry>,
    private val persistEntries: (List<ExtraNamEntry>) -> Unit,
    private val rebuildNativeChain: (List<ExtraNamEntry>) -> String,
    private val readCabinetPosition: (Int) -> Int = { it },
    private val persistCabinetPosition: (Int) -> Unit = {},
    private val setCabinetPosition: (Int) -> Unit = {},
    private val readFxEntries: () -> MutableList<FxImpulseEntry> = { mutableListOf() },
    private val persistFxEntries: (List<FxImpulseEntry>) -> Unit = {},
    private val setFxPosition: (Int, Int) -> Unit = { _, _ -> },
    private val readNativeEntries: () -> MutableList<FxNativeEntry> = { mutableListOf() },
    private val persistNativeEntries: (List<FxNativeEntry>) -> Unit = {},
    private val syncNativeChain: (List<FxNativeEntry>) -> Unit = {},
    private val maxNamBlocks: Int = 4,
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

        entries.removeAt(chainIndex)
        val result = rebuildNativeChain(entries)
        if (entries.isEmpty() || result.startsWith(NAM_CHAIN_READY_PREFIX)) {
            persistEntries(entries)
            shiftFollowingModulesAfterRemoval(chainIndex, entries.size)
        }
        return NamBlockRemovalResult(result, entries.size)
    }

    private fun shiftFollowingModulesAfterRemoval(removedIndex: Int, newNamCount: Int) {
        val oldNamCount = newNamCount + 1
        val cabinetPosition = readCabinetPosition(oldNamCount).coerceIn(0, oldNamCount)
        if (removedIndex < cabinetPosition) {
            val next = cabinetPosition - 1
            persistCabinetPosition(next)
            setCabinetPosition(next)
        }

        val fxEntries = readFxEntries()
        var fxChanged = false
        val shiftedFx = fxEntries.map { entry ->
            val next = if (removedIndex < entry.position) {
                (entry.position - 1).coerceIn(0, newNamCount)
            } else {
                entry.position.coerceIn(0, newNamCount)
            }
            if (next != entry.position) fxChanged = true
            entry.copy(position = next)
        }
        if (fxChanged) {
            persistFxEntries(shiftedFx)
            shiftedFx.forEachIndexed { slot, entry -> setFxPosition(slot, entry.position) }
        }

        val nativeEntries = readNativeEntries()
        var nativeChanged = false
        val shiftedNative = nativeEntries.map { entry ->
            // MAX_NAM_BLOCKS + 1 is the persistent post-chain sentinel.
            val next = if (entry.position in (removedIndex + 1)..maxNamBlocks) {
                entry.position - 1
            } else {
                entry.position
            }
            if (next != entry.position) nativeChanged = true
            entry.copy(position = next)
        }
        if (nativeChanged) {
            persistNativeEntries(shiftedNative)
            syncNativeChain(shiftedNative)
        }
    }

    private companion object {
        const val NAM_CHAIN_READY_PREFIX = "NAM CHAIN READY"
    }
}
