package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.NamEqBandType
import com.pedro.tone3000m1.domain.model.NamEqDefaults

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
    private val setEqBandParamsNative: (Int, Int, String, Float, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
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
        val updated = setControl(chainIndex, db, Control.EQ_BAND(band)) ?: return
        applyEqBand(chainIndex, band, updated)
    }

    fun setEqFrequency(chainIndex: Int, band: Int, frequencyHz: Float) {
        if (band !in EQ_BAND_INDICES) return
        val updated = updateEqBand(chainIndex, band) { entry ->
            entry.copy(eqFrequenciesHz = entry.eqFrequenciesHz.withValue(band, frequencyHz.coerceIn(20f, 20000f), NamEqDefaults.frequenciesHz))
        } ?: return
        applyEqBand(chainIndex, band, updated)
    }

    fun setEqQ(chainIndex: Int, band: Int, q: Float) {
        if (band !in EQ_BAND_INDICES) return
        val updated = updateEqBand(chainIndex, band) { entry ->
            entry.copy(eqQValues = entry.eqQValues.withValue(band, q.coerceIn(0.1f, 10f), NamEqDefaults.qValues))
        } ?: return
        applyEqBand(chainIndex, band, updated)
    }

    fun setEqType(chainIndex: Int, band: Int, type: String) {
        if (band !in EQ_BAND_INDICES) return
        val updated = updateEqBand(chainIndex, band) { entry ->
            entry.copy(eqTypes = entry.eqTypes.withValue(band, NamEqBandType.coerce(band, type), NamEqDefaults.types))
        } ?: return
        applyEqBand(chainIndex, band, updated)
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

    /** Restores block controls while retaining the selected NAM and its metadata. */
    fun resetParameters(chainIndex: Int) {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return
        val isPedal = current.moduleType.equals("PEDAL", ignoreCase = true)
        val defaultFull = !isPedal
        val qualityResult = if (isPedal) null else setQualityNative(chainIndex, defaultFull)
        val expectedQualityResult = if (defaultFull) "A2 FULL ACTIVE" else "A2 LITE ACTIVE"
        val full = if (qualityResult == expectedQualityResult) defaultFull else current.a2Full
        val updated = current.copy(
            bypass = false,
            gainDb = 0f,
            inGainDb = 0f,
            mix = 1f,
            eqLowDb = 0f,
            eqMidDb = 0f,
            eqHighDb = 0f,
            eqBand3Db = 0f,
            eqBand4Db = 0f,
            eqBand5Db = 0f,
            eqFrequenciesHz = NamEqDefaults.frequenciesHz,
            eqQValues = NamEqDefaults.qValues,
            eqTypes = NamEqDefaults.types,
            eqPre = false,
            eqEnabled = true,
            normalize = !isPedal,
            a2Full = full,
        )
        entries[chainIndex] = updated
        persistEntry(chainIndex, updated)

        setBypassNative(chainIndex, false)
        setGainNative(chainIndex, 0f)
        setInGainNative(chainIndex, 0f)
        setMixNative(chainIndex, 1f)
        repeat(6) { band -> applyEqBand(chainIndex, band, updated) }
        setEqPositionNative(chainIndex, false)
        setEqEnabledNative(chainIndex, true)
        setNormalizeNative(chainIndex, !isPedal)
        publishStatus(
            if (qualityResult != null && qualityResult != expectedQualityResult) qualityResult
            else "Block parameters reset. NAM kept loaded."
        )
    }

    private fun setControl(chainIndex: Int, rawDb: Double, control: Control): ExtraNamEntry? {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return null
        val min = if (control is Control.EQ_BAND) -15f else -24f
        val max = if (control == Control.GAIN) 24f else 15f
        val value = rawDb.toFloat().coerceIn(min, max)
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
        return updated
    }

    private fun updateEqBand(
        chainIndex: Int,
        band: Int,
        update: (ExtraNamEntry) -> ExtraNamEntry,
    ): ExtraNamEntry? {
        val entries = readEntries()
        val current = entries.getOrNull(chainIndex) ?: return null
        val updated = update(current)
        entries[chainIndex] = updated
        persistEntry(chainIndex, updated)
        return updated
    }

    private fun applyEqBand(chainIndex: Int, band: Int, entry: ExtraNamEntry) {
        setEqBandParamsNative(
            chainIndex,
            band,
            entry.eqTypes.getOrElse(band) { NamEqDefaults.types[band] },
            entry.eqFrequenciesHz.getOrElse(band) { NamEqDefaults.frequenciesHz[band] },
            listOf(entry.eqLowDb, entry.eqMidDb, entry.eqHighDb, entry.eqBand3Db, entry.eqBand4Db, entry.eqBand5Db)
                .getOrElse(band) { 0f },
            entry.eqQValues.getOrElse(band) { NamEqDefaults.qValues[band] },
        )
    }

    private fun <T> List<T>.withValue(index: Int, value: T, defaults: List<T>): List<T> =
        (0 until 6).map { position -> if (position == index) value else getOrElse(position) { defaults[position] } }

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
