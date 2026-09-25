package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.FxNativeEntry

/** Coordinates persistence and native synchronization when built-in FX blocks are added or removed. */
internal class FxNativeChainController(
    private val readEntries: () -> MutableList<FxNativeEntry>,
    private val persistEntries: (List<FxNativeEntry>) -> Unit,
    private val isAudioRunning: () -> Boolean,
    private val syncNativeChain: (List<FxNativeEntry>) -> Unit,
    private val restartAudio: () -> String,
    private val hasOtherModules: () -> Boolean,
    private val publishStatus: (String) -> Unit,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    fun add(effect: Int): Boolean {
        val entries = readEntries()
        if (entries.size >= maxEntries) {
            publishStatus("FXNATIVE CHAIN FULL\nMaximum $maxEntries native effects.")
            return false
        }

        val wasRunning = isAudioRunning()
        val selected = effect.coerceIn(MIN_EFFECT, MAX_EFFECT)
        entries.add(
            FxNativeEntry(
                effect = selected,
                param1 = when (selected) { 0, 1 -> 350f; 2 -> 1500f; else -> 150f },
                param2 = when (selected) { 0, 1 -> 0.35f; 2 -> 0.5f; else -> 5000f },
            ),
        )
        persistEntries(entries)
        syncNativeChain(entries)
        val audio = if (wasRunning) restartAudio() else ""
        publishStatus("FXNATIVE ADDED\nStereo post NAM/CAB\n$audio")
        return true
    }

    fun remove(nativeIndex: Int): Boolean {
        val entries = readEntries()
        if (nativeIndex !in entries.indices) return false

        val wasRunning = isAudioRunning()
        entries.removeAt(nativeIndex)
        persistEntries(entries)
        syncNativeChain(entries)
        val audio = if (wasRunning && hasOtherModules()) restartAudio() else ""
        publishStatus("FXNATIVE REMOVED\n$audio")
        return true
    }

    private companion object {
        const val MAX_ENTRIES = 8
        const val MIN_EFFECT = 0
        const val MAX_EFFECT = 3
    }
}
