package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry

/** Applies parameter changes to persisted and native file-backed and built-in FX chains. */
internal class FxParameterController(
    private val readFxEntries: () -> MutableList<FxImpulseEntry>,
    private val persistFxEntries: (List<FxImpulseEntry>) -> Unit,
    private val setFxBypassNative: (Int, Boolean) -> Unit,
    private val setFxMixNative: (Int, Float) -> Unit,
    private val readNativeEntries: () -> MutableList<FxNativeEntry>,
    private val persistNativeEntries: (List<FxNativeEntry>) -> Unit,
    private val setNativeBypass: (Int, Boolean) -> Unit,
    private val setNativeMix: (Int, Float) -> Unit,
    private val setNativeParameter: (Int, Int, Float) -> Unit,
    private val isAudioRunning: () -> Boolean,
    private val syncNativeChain: (List<FxNativeEntry>) -> Unit,
    private val restartAudio: () -> Unit,
) {
    fun setFxBypass(fxIndex: Int, bypassed: Boolean) {
        val entries = readFxEntries()
        val entry = entries.getOrNull(fxIndex) ?: return
        entries[fxIndex] = entry.copy(bypass = bypassed)
        persistFxEntries(entries)
        setFxBypassNative(fxIndex, bypassed)
    }

    fun setFxMix(fxIndex: Int, mix: Double) {
        val entries = readFxEntries()
        val entry = entries.getOrNull(fxIndex) ?: return
        val value = mix.toFloat().coerceIn(0f, 1f)
        entries[fxIndex] = entry.copy(mix = value)
        persistFxEntries(entries)
        setFxMixNative(fxIndex, value)
    }

    fun setNativeBypass(nativeIndex: Int, bypassed: Boolean) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        entries[nativeIndex] = entry.copy(bypass = bypassed)
        persistNativeEntries(entries)
        setNativeBypass(nativeIndex, bypassed)
    }

    fun setNativeMix(nativeIndex: Int, mix: Double) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        val value = mix.toFloat().coerceIn(0f, 1f)
        entries[nativeIndex] = entry.copy(mix = value)
        persistNativeEntries(entries)
        setNativeMix(nativeIndex, value)
    }

    fun setNativeParameter(nativeIndex: Int, parameter: Int, value: Double) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        if (parameter !in 0..2) return
        val normalized = normalizeParameter(entry.effect, parameter, value.toFloat())
        entries[nativeIndex] = when (parameter) {
            0 -> entry.copy(param1 = normalized)
            1 -> entry.copy(param2 = normalized)
            else -> entry.copy(param3 = normalized)
        }
        persistNativeEntries(entries)
        setNativeParameter(nativeIndex, parameter, normalized)
    }

    fun setNativeType(nativeIndex: Int, effect: Int) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        val wasRunning = isAudioRunning()
        val selected = effect.coerceIn(0, 3)
        entries[nativeIndex] = entry.copy(
            effect = selected,
            param1 = when (selected) { 0, 1 -> 350f; 2 -> 1500f; else -> 150f },
            param2 = when (selected) { 0, 1 -> 0.35f; 2 -> 0.5f; else -> 5000f },
            param3 = 12f,
        )
        persistNativeEntries(entries)
        syncNativeChain(entries)
        if (wasRunning) restartAudio()
    }

    private fun normalizeParameter(effect: Int, parameter: Int, value: Float): Float = when (parameter) {
        0 -> value.coerceIn(
            if (effect < 2) 20f else if (effect == 2) 500f else 50f,
            if (effect < 2) 2000f else if (effect == 2) 5000f else 250f,
        )
        1 -> value.coerceIn(
            if (effect < 2) 0f else if (effect == 2) 0f else 1000f,
            if (effect < 2) 0.94f else if (effect == 2) 1f else 10000f,
        )
        else -> value.coerceIn(-12f, 12f)
    }
}
