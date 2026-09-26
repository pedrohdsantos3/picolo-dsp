package com.pedro.tone3000m1.data.repository

import com.pedro.tone3000m1.domain.model.PresetData
import com.pedro.tone3000m1.domain.repository.PresetRepository as PresetRepositoryContract
import java.io.File

/** Persists presets in the existing preferences and file formats. */
internal class PresetRepositoryImpl(
    private val preferences: DataStorePreferenceCache,
    private val filesDirectory: File,
    private val presetCount: Int,
    private val maxNamBlocks: Int,
) : PresetRepositoryContract {
    private fun key(slot: Int, field: String): String = "preset_${slot}_$field"

    private fun file(slot: Int): File = File(filesDirectory, "preset-$slot.nam")

    override fun read(slot: Int): PresetData? {
        if (slot !in 1..presetCount || !preferences.getBoolean(key(slot, "saved"), false)) {
            return null
        }

        val path = preferences.getString(key(slot, "model_path"), null) ?: return null
        val modelFile = File(path)
        if (!modelFile.exists()) return null

        return PresetData(
            slot = slot,
            modelPath = path,
            modelName = preferences.getString(key(slot, "model_name"), modelFile.name) ?: modelFile.name,
            modelSize = preferences.getString(key(slot, "model_size"), "unknown") ?: "unknown",
            toneId = preferences.getString(key(slot, "tone_id"), null),
            toneTitle = preferences.getString(key(slot, "tone_title"), null),
            moduleType = preferences.getString(key(slot, "model_type"), "AMP") ?: "AMP",
            inputGainDb = preferences.getFloat(key(slot, "input_gain_db"), 0.0f),
            outputGainDb = preferences.getFloat(key(slot, "output_gain_db"), 0.0f),
            inputChannel = preferences.getInt(key(slot, "input_channel"), 0),
            outputPair = preferences.getInt(key(slot, "output_pair"), 0),
            gateEnabled = preferences.getBoolean(key(slot, "gate_enabled"), false),
            gateThresholdDb = preferences.getFloat(key(slot, "gate_threshold_db"), -65.0f),
            eqLowDb = preferences.getFloat(key(slot, "eq_low_db"), 0.0f),
            eqMidDb = preferences.getFloat(key(slot, "eq_mid_db"), 0.0f),
            eqHighDb = preferences.getFloat(key(slot, "eq_high_db"), 0.0f),
            namBypass = preferences.getBoolean(key(slot, "nam_bypass"), false),
            namGainDb = preferences.getFloat(key(slot, "nam_gain_db"), -15.0f),
            namInGainDb = preferences.getFloat(key(slot, "nam_in_gain_db"), 0.0f),
            namMix = preferences.getFloat(key(slot, "nam_mix"), 1.0f),
            namEqDb = NAM_EQ_FIELDS.map { field -> preferences.getFloat(key(slot, field), 0.0f) },
            namEqPre = preferences.getBoolean(key(slot, "nam_eq_pre"), false),
            namNormalize = preferences.getBoolean(key(slot, "nam_normalize"), true),
            namA2Full = preferences.getBoolean(key(slot, "nam_a2_full"), false),
            extraNamChainJson = preferences.getString(key(slot, "extra_nam_chain"), "[]") ?: "[]",
            cabinetIrPath = preferences.getString(key(slot, "cabinet_ir_path"), null),
            cabinetIrTitle = preferences.getString(key(slot, "cabinet_ir_title"), "") ?: "",
            cabinetIrToneId = preferences.getString(key(slot, "cabinet_ir_tone_id"), "") ?: "",
            cabinetIrModuleType = preferences.getString(key(slot, "cabinet_ir_module_type"), "IR") ?: "IR",
            cabinetIrBypass = preferences.getBoolean(key(slot, "cabinet_ir_bypass"), false),
            cabinetIrPosition = preferences.getInt(key(slot, "cabinet_ir_position"), maxNamBlocks),
            cabinetIrInGain = preferences.getFloat(key(slot, "cabinet_ir_in_gain"), 0.0f),
            cabinetIrOutGain = preferences.getFloat(key(slot, "cabinet_ir_out_gain"), 0.0f),
            cabinetIrMix = preferences.getFloat(key(slot, "cabinet_ir_mix"), 1.0f),
            cabinetIrEqPre = preferences.getBoolean(key(slot, "cabinet_ir_eq_pre"), false),
            cabinetIrEqDb = CABINET_IR_EQ_FIELDS.map { field -> preferences.getFloat(key(slot, field), 0.0f) },
        )
    }

    /** Copies the active model and saves the current audio settings to a slot. */
    override fun saveCurrent(slot: Int, namBypassFallback: Boolean): String {
        require(slot in 1..presetCount) { "Invalid preset slot." }

        val sourcePath = preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null)
            ?: error("No current model path.")
        val source = File(sourcePath)
        check(source.exists()) { "Current model file does not exist." }

        val destination = file(slot)
        if (source.canonicalFile != destination.canonicalFile) {
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        check(destination.exists() && destination.length() > 0L) { "Preset NAM copy is empty." }

        val cabinetPath = preferences.getString(PresetPreferenceKeys.CABINET_IR_PATH, null)
            ?.let { copyCabinetForSlot(slot, it) }
        val modelName = preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, source.name) ?: source.name
        val modelSize = preferences.getString(PresetPreferenceKeys.LAST_MODEL_SIZE, "unknown") ?: "unknown"
        val toneId = preferences.getString(PresetPreferenceKeys.LAST_TONE_ID, null)
        val toneTitle = preferences.getString(PresetPreferenceKeys.LAST_TONE_TITLE, null)

        val editor = preferences.edit()
            .putInt(PresetPreferenceKeys.ACTIVE_PRESET_SLOT, slot)
            .putBoolean(key(slot, "saved"), true)
            .putString(key(slot, "model_path"), destination.absolutePath)
            .putString(key(slot, "model_name"), modelName)
            .putString(key(slot, "model_size"), modelSize)
            .putString(key(slot, "tone_id"), toneId)
            .putString(key(slot, "tone_title"), toneTitle)
            .putString(key(slot, "model_type"), preferences.getString(PresetPreferenceKeys.LAST_MODEL_TYPE, "AMP") ?: "AMP")
            .putFloat(key(slot, "input_gain_db"), preferences.getFloat(PresetPreferenceKeys.INPUT_GAIN, 0.0f))
            .putFloat(key(slot, "output_gain_db"), preferences.getFloat(PresetPreferenceKeys.OUTPUT_GAIN, 0.0f))
            .putInt(key(slot, "input_channel"), preferences.getInt(PresetPreferenceKeys.INPUT_CHANNEL, 0))
            .putInt(key(slot, "output_pair"), preferences.getInt(PresetPreferenceKeys.OUTPUT_PAIR, 0))
            .putBoolean(key(slot, "gate_enabled"), preferences.getBoolean(PresetPreferenceKeys.GATE_ENABLED, false))
            .putFloat(key(slot, "gate_threshold_db"), preferences.getFloat(PresetPreferenceKeys.GATE_THRESHOLD, -65.0f))
            .putFloat(key(slot, "eq_low_db"), preferences.getFloat(PresetPreferenceKeys.EQ_LOW, 0.0f))
            .putFloat(key(slot, "eq_mid_db"), preferences.getFloat(PresetPreferenceKeys.EQ_MID, 0.0f))
            .putFloat(key(slot, "eq_high_db"), preferences.getFloat(PresetPreferenceKeys.EQ_HIGH, 0.0f))
            .putString(key(slot, "extra_nam_chain"), preferences.getString(PresetPreferenceKeys.EXTRA_NAM_CHAIN, "[]") ?: "[]")
            .putString(key(slot, "cabinet_ir_path"), cabinetPath)
            .putString(key(slot, "cabinet_ir_title"), preferences.getString(PresetPreferenceKeys.CABINET_IR_TITLE, "") ?: "")
            .putString(key(slot, "cabinet_ir_tone_id"), preferences.getString(PresetPreferenceKeys.CABINET_IR_TONE_ID, "") ?: "")
            .putString(key(slot, "cabinet_ir_module_type"), preferences.getString(PresetPreferenceKeys.CABINET_IR_TYPE, "IR") ?: "IR")
            .putBoolean(key(slot, "cabinet_ir_bypass"), preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_BYPASS, false))
            .putInt(key(slot, "cabinet_ir_position"), preferences.getInt(PresetPreferenceKeys.CABINET_IR_POSITION, maxNamBlocks))
            .putFloat(key(slot, "cabinet_ir_in_gain"), preferences.getFloat(PresetPreferenceKeys.CABINET_IR_IN_GAIN, 0.0f))
            .putFloat(key(slot, "cabinet_ir_out_gain"), preferences.getFloat(PresetPreferenceKeys.CABINET_IR_OUT_GAIN, 0.0f))
            .putFloat(key(slot, "cabinet_ir_mix"), preferences.getFloat(PresetPreferenceKeys.CABINET_IR_MIX, 1.0f))
            .putBoolean(key(slot, "nam_bypass"), preferences.getBoolean(PresetPreferenceKeys.NAM_BYPASS, namBypassFallback))
            .putFloat(key(slot, "nam_gain_db"), preferences.getFloat(PresetPreferenceKeys.NAM_GAIN_DB, -15.0f))
            .putFloat(key(slot, "nam_in_gain_db"), preferences.getFloat(PresetPreferenceKeys.NAM_IN_GAIN_DB, 0.0f))
            .putFloat(key(slot, "nam_mix"), preferences.getFloat(PresetPreferenceKeys.NAM_MIX, 1.0f))
            .putBoolean(key(slot, "nam_eq_pre"), preferences.getBoolean(PresetPreferenceKeys.NAM_EQ_PRE, false))
            .putBoolean(key(slot, "nam_normalize"), preferences.getBoolean(PresetPreferenceKeys.NAM_NORMALIZE, true))
            .putBoolean(key(slot, "nam_a2_full"), preferences.getBoolean(PresetPreferenceKeys.NAM_A2_FULL, false))
            .putBoolean(key(slot, "cabinet_ir_eq_pre"), preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_EQ_PRE, false))

        NAM_EQ_FIELDS.forEachIndexed { band, field ->
            editor.putFloat(key(slot, field), preferences.getFloat(NAM_EQ_PREFERENCE_KEYS[band], 0.0f))
        }
        CABINET_IR_EQ_FIELDS.forEachIndexed { band, field ->
            editor.putFloat(key(slot, field), preferences.getFloat(PresetPreferenceKeys.CABINET_IR_EQ_PREFIX + band, 0.0f))
        }
        editor.apply()

        return label(slot)
    }

    /** Applies a previously loaded preset's saved preferences after the engine accepts it. */
    override fun activate(preset: PresetData) {
        val editor = preferences.edit()
            .putInt(PresetPreferenceKeys.ACTIVE_PRESET_SLOT, preset.slot)
            .putString(PresetPreferenceKeys.LAST_MODEL_PATH, preset.modelPath)
            .putString(PresetPreferenceKeys.LAST_MODEL_NAME, preset.modelName)
            .putString(PresetPreferenceKeys.LAST_MODEL_SIZE, preset.modelSize)
            .putString(PresetPreferenceKeys.LAST_TONE_ID, preset.toneId)
            .putString(PresetPreferenceKeys.LAST_TONE_TITLE, preset.toneTitle)
            .putString(PresetPreferenceKeys.LAST_MODEL_TYPE, preset.moduleType)
            .putFloat(PresetPreferenceKeys.INPUT_GAIN, preset.inputGainDb)
            .putFloat(PresetPreferenceKeys.OUTPUT_GAIN, preset.outputGainDb)
            .putInt(PresetPreferenceKeys.INPUT_CHANNEL, preset.inputChannel)
            .putInt(PresetPreferenceKeys.OUTPUT_PAIR, preset.outputPair)
            .putBoolean(PresetPreferenceKeys.GATE_ENABLED, preset.gateEnabled)
            .putFloat(PresetPreferenceKeys.GATE_THRESHOLD, preset.gateThresholdDb)
            .putFloat(PresetPreferenceKeys.EQ_LOW, preset.eqLowDb)
            .putFloat(PresetPreferenceKeys.EQ_MID, preset.eqMidDb)
            .putFloat(PresetPreferenceKeys.EQ_HIGH, preset.eqHighDb)
            .putString(PresetPreferenceKeys.EXTRA_NAM_CHAIN, preset.extraNamChainJson)
            .putString(PresetPreferenceKeys.CABINET_IR_PATH, preset.cabinetIrPath)
            .putString(PresetPreferenceKeys.CABINET_IR_TITLE, preset.cabinetIrTitle)
            .putString(PresetPreferenceKeys.CABINET_IR_TONE_ID, preset.cabinetIrToneId)
            .putString(PresetPreferenceKeys.CABINET_IR_TYPE, preset.cabinetIrModuleType)
            .putBoolean(PresetPreferenceKeys.CABINET_IR_BYPASS, preset.cabinetIrBypass)
            .putInt(PresetPreferenceKeys.CABINET_IR_POSITION, preset.cabinetIrPosition)
            .putFloat(PresetPreferenceKeys.CABINET_IR_IN_GAIN, preset.cabinetIrInGain)
            .putFloat(PresetPreferenceKeys.CABINET_IR_OUT_GAIN, preset.cabinetIrOutGain)
            .putFloat(PresetPreferenceKeys.CABINET_IR_MIX, preset.cabinetIrMix)
            .putBoolean(PresetPreferenceKeys.NAM_BYPASS, preset.namBypass)
            .putFloat(PresetPreferenceKeys.NAM_GAIN_DB, preset.namGainDb)
            .putFloat(PresetPreferenceKeys.NAM_IN_GAIN_DB, preset.namInGainDb)
            .putFloat(PresetPreferenceKeys.NAM_MIX, preset.namMix)
            .putBoolean(PresetPreferenceKeys.NAM_EQ_PRE, preset.namEqPre)
            .putBoolean(PresetPreferenceKeys.NAM_NORMALIZE, preset.namNormalize)
            .putBoolean(PresetPreferenceKeys.NAM_A2_FULL, preset.namA2Full)
            .putBoolean(PresetPreferenceKeys.CABINET_IR_EQ_PRE, preset.cabinetIrEqPre)

        NAM_EQ_PREFERENCE_KEYS.forEachIndexed { band, preferenceKey ->
            editor.putFloat(preferenceKey, preset.namEqDb[band])
        }
        CABINET_IR_EQ_FIELDS.forEachIndexed { band, _ ->
            editor.putFloat(PresetPreferenceKeys.CABINET_IR_EQ_PREFIX + band, preset.cabinetIrEqDb[band])
        }
        editor.apply()
    }

    override fun label(slot: Int): String {
        preferences.getString(key(slot, "custom_label"), null)
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val preset = read(slot) ?: return "Preset $slot • EMPTY"
        return buildString {
            append("Preset $slot • ")
            if (!preset.toneTitle.isNullOrBlank()) append("${preset.toneTitle} • ")
            append(preset.modelName)
        }
    }

    override fun rename(slot: Int, name: String): Boolean {
        if (slot !in 1..presetCount) return false
        preferences.edit().putString(key(slot, "custom_label"), name.trim()).apply()
        return true
    }

    override fun delete(slot: Int): Boolean {
        if (slot !in 1..presetCount) return false

        val prefix = "preset_${slot}_"
        preferences.edit().apply {
            preferences.getAll().keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
        listOf(file(slot), File(filesDirectory, "preset-$slot-cabinet.wav"))
            .forEach { if (it.exists()) it.delete() }
        return true
    }

    override fun move(slot: Int, delta: Int): Boolean {
        val target = slot + delta.coerceIn(-1, 1)
        if (slot !in 1..presetCount || target !in 1..presetCount || slot == target) return false

        val first = valuesFor(slot)
        val second = valuesFor(target)
        preferences.edit().apply {
            (first.keys + second.keys).forEach { field ->
                remove(key(slot, field))
                remove(key(target, field))
            }
            first.forEach { (field, value) -> putValue(key(target, field), value) }
            second.forEach { (field, value) -> putValue(key(slot, field), value) }
        }.apply()
        return true
    }

    private fun valuesFor(slot: Int): Map<String, Any?> {
        val prefix = "preset_${slot}_"
        return preferences.getAll().filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
    }

    private fun copyCabinetForSlot(slot: Int, sourcePath: String): String? {
        val source = File(sourcePath)
        if (!source.exists()) return null

        val destination = File(filesDirectory, "preset-$slot-cabinet.wav")
        if (source.canonicalFile != destination.canonicalFile) {
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return destination.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    }

    private fun DataStorePreferenceCache.Editor.putValue(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private companion object {
        val NAM_EQ_FIELDS = listOf(
            "nam_eq_low_db",
            "nam_eq_mid_db",
            "nam_eq_high_db",
            "nam_eq_band3_db",
            "nam_eq_band4_db",
            "nam_eq_band5_db",
        )
        val NAM_EQ_PREFERENCE_KEYS = listOf(
            PresetPreferenceKeys.NAM_EQ_LOW_DB,
            PresetPreferenceKeys.NAM_EQ_MID_DB,
            PresetPreferenceKeys.NAM_EQ_HIGH_DB,
            PresetPreferenceKeys.NAM_EQ_BAND3_DB,
            PresetPreferenceKeys.NAM_EQ_BAND4_DB,
            PresetPreferenceKeys.NAM_EQ_BAND5_DB,
        )
        val CABINET_IR_EQ_FIELDS = (0..5).map { "cabinet_ir_eq_$it" }
    }
}
