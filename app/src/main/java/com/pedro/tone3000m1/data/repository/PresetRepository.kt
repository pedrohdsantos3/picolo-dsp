package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.data.model.PresetData
import java.io.File

/** Reads and organizes the existing preset preferences and files. */
internal class PresetRepository(
    private val preferences: SharedPreferences,
    private val filesDirectory: File,
    private val presetCount: Int,
    private val maxNamBlocks: Int,
) {
    fun key(slot: Int, field: String): String = "preset_${slot}_$field"

    fun file(slot: Int): File = File(filesDirectory, "preset-$slot.nam")

    fun read(slot: Int): PresetData? {
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
            inputGainDb = preferences.getFloat(key(slot, "input_gain_db"), 0.0f),
            outputGainDb = preferences.getFloat(key(slot, "output_gain_db"), 0.0f),
            inputChannel = preferences.getInt(key(slot, "input_channel"), 0),
            outputPair = preferences.getInt(key(slot, "output_pair"), 0),
            gateEnabled = preferences.getBoolean(key(slot, "gate_enabled"), false),
            gateThresholdDb = preferences.getFloat(key(slot, "gate_threshold_db"), -65.0f),
            eqLowDb = preferences.getFloat(key(slot, "eq_low_db"), 0.0f),
            eqMidDb = preferences.getFloat(key(slot, "eq_mid_db"), 0.0f),
            eqHighDb = preferences.getFloat(key(slot, "eq_high_db"), 0.0f),
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
        )
    }

    fun label(slot: Int): String {
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

    fun rename(slot: Int, name: String): Boolean {
        if (slot !in 1..presetCount) return false
        preferences.edit().putString(key(slot, "custom_label"), name.trim()).apply()
        return true
    }

    fun delete(slot: Int): Boolean {
        if (slot !in 1..presetCount) return false

        val prefix = "preset_${slot}_"
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
        listOf(file(slot), File(filesDirectory, "preset-$slot-cabinet.wav"))
            .forEach { if (it.exists()) it.delete() }
        return true
    }

    fun move(slot: Int, delta: Int): Boolean {
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
        return preferences.all.filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any?) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
