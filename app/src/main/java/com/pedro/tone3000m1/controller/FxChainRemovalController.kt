package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.FxImpulseEntry

/** Removes a file-backed FX block and reloads the remaining slots in order. */
internal class FxChainRemovalController(
    private val readEntries: () -> MutableList<FxImpulseEntry>,
    private val persistEntries: (List<FxImpulseEntry>) -> Unit,
    private val isAudioRunning: () -> Boolean,
    private val clearFxSlot: (Int) -> Unit,
    private val loadFxSlot: (Int, String) -> String,
    private val setFxBypass: (Int, Boolean) -> Unit,
    private val setFxMix: (Int, Float) -> Unit,
    private val namBlockCount: () -> Int,
    private val setFxPosition: (Int, Int) -> Unit,
    private val cabinetPathExists: () -> Boolean,
    private val deleteFile: (String) -> Unit,
    private val restartAudio: () -> String,
    private val publishStatus: (String) -> Unit,
    private val maxSlots: Int = MAX_SLOTS,
    private val nativeFxCount: () -> Int = { 0 },
) {
    fun remove(fxIndex: Int): Boolean {
        val entries = readEntries()
        if (fxIndex !in entries.indices) return false

        val wasRunning = isAudioRunning()
        val originalEntries = entries.toList()
        val removed = entries.removeAt(fxIndex)
        repeat(maxSlots, clearFxSlot)
        entries.forEachIndexed { slot, item ->
            val loaded = loadFxSlot(slot, item.path)
            if (!loaded.startsWith(FX_LOADED_PREFIX)) {
                val restoreFailures = restoreChain(originalEntries)
                val audio = if (wasRunning && restoreFailures.isEmpty()) restartAudio() else ""
                publishStatus(
                    buildString {
                        append("FX REMOVAL CANCELLED\n$loaded")
                        if (restoreFailures.isNotEmpty()) append("\nRestore failed: ${restoreFailures.joinToString("; ")}")
                        if (audio.isNotBlank()) append("\n$audio")
                    },
                )
                return false
            }
            setFxBypass(slot, item.bypass)
            setFxMix(slot, item.mix)
            setFxPosition(slot, item.position.coerceIn(0, namBlockCount()))
        }
        persistEntries(entries)
        try {
            deleteFile(removed.path)
        } catch (_: Exception) {
            // Keep the remaining chain even if obsolete file cleanup fails.
        }
        val hasModules = namBlockCount() > 0 || entries.isNotEmpty() || cabinetPathExists() || nativeFxCount() > 0
        val audio = if (wasRunning && hasModules) restartAudio() else ""
        publishStatus("FX REMOVED\n$audio")
        return true
    }

    private companion object {
        const val MAX_SLOTS = 8
        const val FX_LOADED_PREFIX = "FX LOADED"
    }

    private fun restoreChain(entries: List<FxImpulseEntry>): List<String> {
        val failures = mutableListOf<String>()
        repeat(maxSlots, clearFxSlot)
        entries.forEachIndexed { slot, item ->
            val loaded = loadFxSlot(slot, item.path)
            if (!loaded.startsWith(FX_LOADED_PREFIX)) {
                failures += loaded
            } else {
                setFxBypass(slot, item.bypass)
                setFxMix(slot, item.mix)
                setFxPosition(slot, item.position.coerceIn(0, namBlockCount()))
            }
        }
        persistEntries(entries)
        return failures
    }
}
