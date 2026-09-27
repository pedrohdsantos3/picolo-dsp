package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry

/** Places only a newly imported NAM by type while retaining the user's existing block order. */
internal class NamImportPlacementController(
    private val readNamEntries: () -> MutableList<ExtraNamEntry>,
    private val persistNamEntries: (List<ExtraNamEntry>) -> Unit,
    private val rebuildNamChain: (List<ExtraNamEntry>) -> String,
    private val cabinetIsLoaded: () -> Boolean,
    private val readCabinetPosition: (Int) -> Int,
    private val persistCabinetPosition: (Int) -> Unit,
    private val setCabinetPosition: (Int) -> Unit,
    private val readFxEntries: () -> MutableList<FxImpulseEntry>,
    private val persistFxEntries: (List<FxImpulseEntry>) -> Unit,
    private val setFxPosition: (Int, Int) -> Unit,
    private val readNativeEntries: () -> MutableList<FxNativeEntry> = { mutableListOf() },
    private val persistNativeEntries: (List<FxNativeEntry>) -> Unit = {},
    private val syncNativeChain: (List<FxNativeEntry>) -> Unit = {},
    private val maxNamBlocks: Int = 4,
) {
    /** The Add NAM use case appends first; this moves that one entry into its default type position. */
    fun placeImportedNam(path: String): String? {
        val appendedEntries = readNamEntries()
        val appendedIndex = appendedEntries.indexOfLast { it.path == path }
        if (appendedIndex < 0) return "Imported NAM is missing from the signal chain."

        val imported = appendedEntries[appendedIndex]
        val previousEntries = appendedEntries.toMutableList().apply { removeAt(appendedIndex) }
        val insertionIndex = defaultInsertionIndex(previousEntries, imported.moduleType)
        val orderedEntries = previousEntries.toMutableList().apply { add(insertionIndex, imported) }

        var warning: String? = null
        var effectiveInsertionIndex = insertionIndex
        if (orderedEntries != appendedEntries) {
            val rebuilt = rebuildNamChain(orderedEntries)
            if (rebuilt.startsWith(NAM_CHAIN_READY_PREFIX)) {
                persistNamEntries(orderedEntries)
            } else {
                val restored = rebuildNamChain(appendedEntries)
                effectiveInsertionIndex = appendedIndex
                warning = if (restored.startsWith(NAM_CHAIN_READY_PREFIX)) {
                    "NAM added at the end; automatic placement could not be applied."
                } else {
                    "NAM placement failed and the previous chain could not be fully restored."
                }
            }
        }

        shiftFollowingModules(effectiveInsertionIndex, previousEntries.size)
        return warning
    }

    private fun defaultInsertionIndex(entries: List<ExtraNamEntry>, moduleType: String): Int = when (moduleType.uppercase()) {
        PEDAL_TYPE -> entries.indexOfFirst { it.moduleType.uppercase() == AMP_TYPE }
            .takeIf { it >= 0 } ?: entries.size
        AMP_TYPE -> entries.indexOfLast { it.moduleType.uppercase() == PEDAL_TYPE }
            .takeIf { it >= 0 }
            ?.plus(1) ?: entries.size
        else -> entries.size
    }

    /** Preserve cabinet and space-FX positions when the NAM list grows before them. */
    private fun shiftFollowingModules(insertionIndex: Int, oldNamCount: Int) {
        if (cabinetIsLoaded()) {
            val current = readCabinetPosition(oldNamCount).coerceIn(0, oldNamCount)
            if (insertionIndex <= current) {
                val next = current + 1
                persistCabinetPosition(next)
                setCabinetPosition(next)
            }
        }

        val fxEntries = readFxEntries()
        var changed = false
        val newNamCount = oldNamCount + 1
        val shifted = fxEntries.map { entry ->
            val next = (if (insertionIndex <= entry.position) entry.position + 1 else entry.position)
                .coerceIn(0, newNamCount)
            if (next != entry.position) changed = true
            entry.copy(position = next)
        }
        if (changed) {
            persistFxEntries(shifted)
            shifted.forEachIndexed { slot, entry -> setFxPosition(slot, entry.position) }
        }

        val nativeEntries = readNativeEntries()
        var nativeChanged = false
        val shiftedNative = nativeEntries.map { entry ->
            // Preserve the post-chain sentinel while moving boundary positions with NAM blocks.
            val next = if (entry.position in insertionIndex..maxNamBlocks) {
                entry.position + 1
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
        const val AMP_TYPE = "AMP"
        const val PEDAL_TYPE = "PEDAL"
        const val NAM_CHAIN_READY_PREFIX = "NAM CHAIN READY"
    }
}
