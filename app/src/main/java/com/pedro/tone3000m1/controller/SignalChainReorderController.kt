package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry

/** Coordinates the mixed NAM, cabinet IR, and file-backed FX order with the native graph. */
internal class SignalChainReorderController(
    private val readNamEntries: () -> MutableList<ExtraNamEntry>,
    private val persistNamEntries: (List<ExtraNamEntry>) -> Unit,
    private val readFxEntries: () -> MutableList<FxImpulseEntry>,
    private val persistFxEntries: (List<FxImpulseEntry>) -> Unit,
    private val readCabinetPosition: (Int) -> Int,
    private val persistCabinetPosition: (Int) -> Unit,
    private val setCabinetPosition: (Int) -> Unit,
    private val isAudioRunning: () -> Boolean,
    private val rebuildNamChain: (List<ExtraNamEntry>) -> String,
    private val clearFxSlot: (Int) -> Unit,
    private val loadFxSlot: (Int, String) -> String,
    private val setFxBypass: (Int, Boolean) -> Unit,
    private val setFxMix: (Int, Float) -> Unit,
    private val setFxPosition: (Int, Int) -> Unit,
    private val hasModules: () -> Boolean,
    private val restartAudio: () -> String,
    private val publishStatus: (String) -> Unit,
    private val reportError: (Exception) -> Unit,
    private val maxFxSlots: Int = MAX_FX_SLOTS,
    private val readNativeEntries: () -> MutableList<FxNativeEntry> = { mutableListOf() },
    private val persistNativeEntries: (List<FxNativeEntry>) -> Unit = {},
    private val syncNativeChain: (List<FxNativeEntry>) -> Unit = {},
) {
    fun reorder(requested: List<String>): Boolean = try {
        val entries = readNamEntries()
        val wasRunning = isAudioRunning()
        val fxEntries = readFxEntries()
        val nativeEntries = readNativeEntries()
        val orderedNamIndices = requested
            .filter { it.startsWith(NAM_BLOCK_PREFIX) }
            .mapNotNull { it.removePrefix(NAM_BLOCK_PREFIX).toIntOrNull() }
            .filter { it in entries.indices }
            .distinct()
            .toMutableList()
        entries.indices.forEach { if (it !in orderedNamIndices) orderedNamIndices.add(it) }
        val reorderedNam = orderedNamIndices.map { entries[it] }

        val orderedFxIndices = requested.filter { it.startsWith(FX_BLOCK_PREFIX) }
            .mapNotNull { it.removePrefix(FX_BLOCK_PREFIX).toIntOrNull() }
            .filter { it in fxEntries.indices }
            .distinct()
            .toMutableList()
        fxEntries.indices.forEach { if (it !in orderedFxIndices) orderedFxIndices.add(it) }
        val reorderedFx = orderedFxIndices.map { fxEntries[it] }.toMutableList()

        val orderedNativeIndices = requested.filter { it.startsWith(FX_NATIVE_BLOCK_PREFIX) }
            .mapNotNull { it.removePrefix(FX_NATIVE_BLOCK_PREFIX).toIntOrNull() }
            .filter { it in nativeEntries.indices }
            .distinct()
            .toMutableList()
        nativeEntries.indices.forEach { if (it !in orderedNativeIndices) orderedNativeIndices.add(it) }
        val nativePositions = mutableMapOf<Int, Int>()
        var nativeNamBefore = 0
        requested.forEachIndexed { requestIndex, id ->
            when {
                id.startsWith(NAM_BLOCK_PREFIX) -> nativeNamBefore++
                id.startsWith(FX_NATIVE_BLOCK_PREFIX) -> id.removePrefix(FX_NATIVE_BLOCK_PREFIX)
                    .toIntOrNull()?.let { entryIndex ->
                        val followingMainModule = requested.drop(requestIndex + 1).any {
                            it.startsWith(NAM_BLOCK_PREFIX) || it == CABINET_BLOCK_ID
                        }
                        nativePositions[entryIndex] = if (followingMainModule) {
                            nativeNamBefore.coerceIn(0, MAX_NAM_BLOCKS)
                        } else {
                            POST_CHAIN_POSITION
                        }
                    }
            }
        }
        val reorderedNative = orderedNativeIndices.map { sourceIndex ->
            nativeEntries[sourceIndex].copy(
                position = nativePositions[sourceIndex] ?: POST_CHAIN_POSITION,
            )
        }

        val fxPositions = mutableMapOf<Int, Int>()
        var namBefore = 0
        requested.forEach { id ->
            when {
                id.startsWith(NAM_BLOCK_PREFIX) -> namBefore++
                id.startsWith(FX_BLOCK_PREFIX) -> id.removePrefix(FX_BLOCK_PREFIX).toIntOrNull()?.let {
                    fxPositions[it] = namBefore
                }
            }
        }
        val cabinetPosition = requested.indexOf(CABINET_BLOCK_ID)
            .takeIf { it >= 0 }
            ?.coerceIn(0, reorderedNam.size)
            ?: readCabinetPosition(reorderedNam.size).coerceIn(0, reorderedNam.size)

        val rebuildResult = rebuildNamChain(reorderedNam)
        if (rebuildResult.startsWith(NAM_CHAIN_READY_PREFIX)) {
            persistNamEntries(reorderedNam)
            persistCabinetPosition(cabinetPosition)
            setCabinetPosition(cabinetPosition)
            repeat(maxFxSlots) { clearFxSlot(it) }
            reorderedFx.forEachIndexed { slot, item ->
                val loaded = loadFxSlot(slot, item.path)
                if (!loaded.startsWith(FX_LOADED_PREFIX)) return false
                val requestedIndex = orderedFxIndices.getOrNull(slot)
                val position = requestedIndex?.let(fxPositions::get) ?: reorderedNam.size
                reorderedFx[slot] = item.copy(
                    position = position,
                )
                setFxBypass(slot, item.bypass)
                setFxMix(slot, item.mix)
                setFxPosition(slot, position.coerceIn(0, reorderedNam.size))
            }
            persistFxEntries(reorderedFx)
            persistNativeEntries(reorderedNative)
            syncNativeChain(reorderedNative)
            val audio = if (wasRunning && hasModules()) restartAudio() else ""
            publishStatus(if (audio.isBlank()) rebuildResult else "$rebuildResult\n$audio")
            true
        } else {
            val rollback = rebuildNamChain(entries)
            publishStatus(
                if (rollback.startsWith(NAM_CHAIN_READY_PREFIX)) {
                    "Reordenação cancelada: $rebuildResult"
                } else {
                    "Falha ao reordenar e restaurar cadeia: $rollback"
                },
            )
            false
        }
    } catch (error: Exception) {
        reportError(error)
        false
    }

    private companion object {
        const val MAX_FX_SLOTS = 8
        const val NAM_BLOCK_PREFIX = "nam-"
        const val FX_BLOCK_PREFIX = "fx-"
        const val FX_NATIVE_BLOCK_PREFIX = "fxnative-"
        const val CABINET_BLOCK_ID = "cabinet-ir"
        const val MAX_NAM_BLOCKS = 4
        const val POST_CHAIN_POSITION = MAX_NAM_BLOCKS + 1
        const val NAM_CHAIN_READY_PREFIX = "NAM CHAIN READY"
        const val FX_LOADED_PREFIX = "FX LOADED"
    }
}
