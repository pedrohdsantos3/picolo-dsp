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
                param1 = defaultParam1(selected),
                param2 = defaultParam2(selected),
                param3 = defaultParam3(selected),
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

    companion object {
        const val MAX_ENTRIES = 8
        const val MIN_EFFECT = 0
        const val MAX_EFFECT = 11

        fun defaultParam1(effect: Int) = when (effect) {
            0, 1, 5 -> 350f
            2, 4 -> 2000f
            3 -> 150f
            6, 7 -> 3000f
            8 -> 5000f
            10 -> 350f
            11 -> 8f
            else -> 450f
        }

        fun defaultParam2(effect: Int) = when (effect) {
            0, 1, 5, 10 -> 0.35f
            11 -> 0.8f
            9 -> 0.55f
            2, 4 -> 0.5f
            3 -> 5000f
            6, 8 -> 8500f
            7 -> 40f
            else -> 6000f
        }

        fun defaultParam3(effect: Int) = when (effect) {
            3 -> 0f
            4 -> 6500f
            9 -> 900f
            10 -> 525f
            11 -> 16000f
            5, 6, 8 -> 0.72f
            7 -> 0.9f
            else -> 12f
        }
    }
}
