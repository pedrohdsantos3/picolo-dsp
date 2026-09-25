package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry

/** Coordinates persisted NAM block controls with the native audio engine. */
internal class NamParameterController(
    private val readEntries: () -> MutableList<ExtraNamEntry>,
    private val persistEntry: (Int, ExtraNamEntry) -> Unit,
    private val setBypassNative: (Int, Boolean) -> Unit,
    private val setGainNative: (Int, Float) -> Unit,
    private val setInGainNative: (Int, Float) -> Unit,
    private val setMixNative: (Int, Float) -> Unit,
    private val setEqNative: (Int, Int, Float) -> Unit,
    private val setEqPositionNative: (Int, Boolean) -> Unit,
    private val setEqEnabledNative: (Int, Boolean) -> Unit,
    private val setNormalizeNative: (Int, Boolean) -> Unit,
    private val setQualityNative: (Int, Boolean) -> String,
    private val publishStatus: (String) -> Unit,
) {
    fun setBypass(chainIndex: Int, enabled: Boolean) {
        updateEntry(chainIndex) { it.copy(bypass = enabled) } ?: return
        setBypassNative(chainIndex, enabled)
    }

    fun setGain(chainIndex: Int, db: Double) {
        setControl(chainIndex, db, Control.GAIN)
    }

    fun setInGain(chainIndex: Int, db: Double) {
        val value = db.toFloat().coerceIn(-24f, 24f)
        if (updateEntry(chainIndex) { it.copy(inGainDb = value) } != null) {
            setInGainNative(chainIndex, value)
        }
    }

    fun setMix(chainIndex: Int, mix: Double) {
        val value = mix.toFloat().coerceIn(0f, 1f)
        if (updateEntry(chainIndex) { it.copy(mix = value) } != null) {
            setMixNative(chainIndex, value)
        }
    }

    fun setEq(chainIndex: Int, band: Int, db: Double) {
        if (band !in EQ_BAND_INDICES) return
        setControl(chainIndex, db, Control.EQ_BAND(band))
    }

    fun setEqPosition(chainIndex: Int, pre: Boolean) {
        if (updateEntry(chainIndex) { it.copy(eqPre = pre) } != null) {
            setEqPositionNative(chainIndex, pre)
        }
    }

    fun setEqEnabled(chainIndex: Int, enabled: Boolean) {
        if (updateEntry(chainIndex) { it.copy(eqEnabled = enabled) } != null) {
            setEqEnabledNative(chainIndex, enabled)
        }
    }

    fun setNormalize(chainIndex: Int, enabled: Boolean) {
        if (updateEntry(chainIndex) { it.copy(normalize = enabled) } != null) {
            setNormalizeNative(chainIndex, enabled)
        }
    }

    fun setQuality(chainIndex: Int, full: Boolean) {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return
        if (full && current.moduleType != "AMP") {
            publishStatus("A2 Full is available for AMP blocks only.")
            return
        }
        val result = setQualityNative(chainIndex, full)
        if (!result.startsWith("A2 ")) {
            publishStatus(result)
            return
        }
        val updated = current.copy(a2Full = full)
        entries[chainIndex] = updated
        persistEntry(chainIndex, updated)
        publishStatus(result)
    }

    private fun setControl(chainIndex: Int, rawDb: Double, control: Control) {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return
        val min = if (control == Control.GAIN) -24f else -12f
        val value = rawDb.toFloat().coerceIn(min, 12f)
        val updated = when (control) {
            Control.GAIN -> current.copy(gainDb = value)
            is Control.EQ_BAND -> when (control.index) {
                0 -> current.copy(eqLowDb = value)
                1 -> current.copy(eqMidDb = value)
                2 -> current.copy(eqHighDb = value)
                3 -> current.copy(eqBand3Db = value)
                4 -> current.copy(eqBand4Db = value)
                else -> current.copy(eqBand5Db = value)
            }
        }
        entries[chainIndex] = updated
        persistEntry(chainIndex, updated)
        when (control) {
            Control.GAIN -> setGainNative(chainIndex, value)
            is Control.EQ_BAND -> setEqNative(chainIndex, control.index, value)
        }
    }

    private fun updateEntry(
        chainIndex: Int,
        update: (ExtraNamEntry) -> ExtraNamEntry,
    ): ExtraNamEntry? {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return null
        val updated = update(current)
        entries[chainIndex] = updated
        persistEntry(chainIndex, updated)
        return updated
    }

    private sealed interface Control {
        data object GAIN : Control
        data class EQ_BAND(val index: Int) : Control
    }

    private companion object {
        val EQ_BAND_INDICES = 0 until 6
    }
}
