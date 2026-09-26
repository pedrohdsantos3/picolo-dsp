package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.domain.model.DualDelayTiming

/** Applies parameter changes to persisted and native file-backed and built-in FX chains. */
internal class FxParameterController(
    private val readFxEntries: () -> MutableList<FxImpulseEntry>,
    private val persistFxEntries: (List<FxImpulseEntry>) -> Unit,
    private val setFxBypassNative: (Int, Boolean) -> Unit,
    private val setFxMixNative: (Int, Float) -> Unit,
    private val readNativeEntries: () -> MutableList<FxNativeEntry>,
    private val persistNativeEntries: (List<FxNativeEntry>) -> Unit,
    private val applyNativeBypass: (Int, Boolean) -> Unit,
    private val applyNativeMix: (Int, Float) -> Unit,
    private val applyNativeParameter: (Int, Int, Float) -> Unit,
    private val applyNativeOutputGainDb: (Int, Float) -> Unit = { _, _ -> },
    private val readGlobalTapTempoBpm: () -> Float = { 120f },
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
        applyNativeBypass(nativeIndex, bypassed)
    }

    fun setNativeMix(nativeIndex: Int, mix: Double) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        val value = mix.toFloat().coerceIn(0f, 1f)
        entries[nativeIndex] = entry.copy(mix = value)
        persistNativeEntries(entries)
        applyNativeMix(nativeIndex, value)
    }

    fun setNativeOutputGainDb(nativeIndex: Int, gainDb: Double) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex)?.takeIf { it.effect == 11 } ?: return
        val normalized = gainDb.toFloat().coerceIn(-12f, 12f)
        entries[nativeIndex] = entry.copy(outputGainDb = normalized)
        persistNativeEntries(entries)
        applyNativeOutputGainDb(nativeIndex, normalized)
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
        applyNativeParameter(nativeIndex, parameter, normalized)
    }

    fun setNativeTiming(
        nativeIndex: Int,
        tempoSync: Boolean,
        leftNote: String,
        rightNote: String,
    ) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex)?.takeIf { isTempoEffect(it.effect) } ?: return
        val updated = entry.copy(
            tempoSync = tempoSync,
            leftNote = leftNote.takeIf { it in DualDelayTiming.noteValues } ?: "1/4",
            rightNote = rightNote.takeIf { it in DualDelayTiming.noteValues } ?: "1/8.",
        )
        entries[nativeIndex] = updated
        persistNativeEntries(entries)
        if (tempoSync) {
            applyTiming(nativeIndex, updated, readGlobalTapTempoBpm())
        } else {
            when (updated.effect) {
                10 -> {
                    applyNativeParameter(nativeIndex, 0, updated.param1)
                    applyNativeParameter(nativeIndex, 2, updated.param3)
                }
                11 -> applyNativeParameter(nativeIndex, 1, updated.param2)
                else -> applyNativeParameter(nativeIndex, 0, updated.param1)
            }
        }
    }

    fun setGlobalTapTempoBpm(bpm: Float) {
        val normalized = bpm.coerceIn(40f, 240f)
        readNativeEntries().forEachIndexed { index, entry ->
            if (entry.tempoSync && isTempoEffect(entry.effect)) applyTiming(index, entry, normalized)
        }
    }

    private fun applyTiming(nativeIndex: Int, entry: FxNativeEntry, bpm: Float) {
        if (!entry.tempoSync) return
        when (entry.effect) {
            10 -> {
                applyNativeParameter(nativeIndex, 0, DualDelayTiming.toMilliseconds(entry.leftNote, bpm))
                applyNativeParameter(nativeIndex, 2, DualDelayTiming.toMilliseconds(entry.rightNote, bpm))
            }
            11 -> applyNativeParameter(nativeIndex, 1, DualDelayTiming.toHertz(entry.leftNote, bpm))
            else -> applyNativeParameter(nativeIndex, 0, DualDelayTiming.toMilliseconds(entry.leftNote, bpm))
        }
    }

    private fun isTempoEffect(effect: Int): Boolean = effect in setOf(0, 1, 5, 9, 10, 11)

    fun setNativeType(nativeIndex: Int, effect: Int) {
        val entries = readNativeEntries()
        val entry = entries.getOrNull(nativeIndex) ?: return
        val wasRunning = isAudioRunning()
        val selected = effect.coerceIn(FxNativeChainController.MIN_EFFECT, FxNativeChainController.MAX_EFFECT)
        entries[nativeIndex] = entry.copy(
            effect = selected,
            param1 = FxNativeChainController.defaultParam1(selected),
            param2 = FxNativeChainController.defaultParam2(selected),
            param3 = FxNativeChainController.defaultParam3(selected),
            outputGainDb = 0f,
        )
        persistNativeEntries(entries)
        syncNativeChain(entries)
        if (wasRunning) restartAudio()
    }

    private fun normalizeParameter(effect: Int, parameter: Int, value: Float): Float {
        val range = when (parameter) {
            0 -> when (effect) {
                0, 1, 5, 10 -> 20f..2000f
                11 -> 0f..20f
                2, 4 -> 500f..5000f
                3 -> 50f..250f
                6, 7 -> 500f..8000f
                8 -> 500f..10000f
                else -> 70f..1800f
            }
            1 -> when (effect) {
                0, 1, 5, 9, 10 -> 0f..0.96f
                11 -> 0.1f..8f
                2, 4 -> 0f..1f
                3 -> 1000f..10000f
                6 -> 1000f..12000f
                7 -> 0f..300f
                else -> 100f..18500f
            }
            else -> when (effect) {
                3 -> -12f..12f
                4 -> 500f..12000f
                9 -> 500f..9500f
                10 -> 20f..2000f
                11 -> 1000f..20000f
                7 -> 0.45f..1f
                5, 6, 8 -> 0f..1f
                else -> 0f..1f
            }
        }
        return value.coerceIn(range.start, range.endInclusive)
    }
}
