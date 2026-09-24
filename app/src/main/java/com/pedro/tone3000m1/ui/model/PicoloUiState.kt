package com.pedro.tone3000m1.ui.model

import org.json.JSONObject
import java.util.Locale

internal data class UiModule(
    val id: String,
    val type: String,
    val name: String,
    val moduleType: String = "AMP",
    val index: Int = -1,
    val bypass: Boolean = false,
    val gainDb: Float = -15f,
    val inGainDb: Float = 0f,
    val mix: Float = 1f,
    val eqEnabled: Boolean = true,
    val eqPre: Boolean = false,
    val normalize: Boolean = true,
    val a2Full: Boolean = false,
    val eqBands: List<Float> = List(6) { 0f },
    val nativeEffect: Int = 0,
    val nativeParam1: Float = 350f,
    val nativeParam2: Float = 0.35f,
    val nativeParam3: Float = 12f,
)

internal data class UiPreset(val slot: Int, val label: String, val saved: Boolean)

internal data class PicoloUiState(
    val running: Boolean = false,
    val inputDbFs: Float? = null,
    val outputDbFs: Float? = null,
    val bypass: Boolean = false,
    val modelName: String = "No capture loaded",
    val toneTitle: String = "",
    val activePresetSlot: Int = 0,
    val device: String = "Audio interface: auto detect",
    val inputGain: Float = 0f,
    val outputGain: Float = 0f,
    val gateEnabled: Boolean = false,
    val gateThreshold: Float = -65f,
    val eqEnabled: Boolean = true,
    val eqLow: Float = 0f,
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val cabinetLoaded: Boolean = false,
    val cabinetName: String = "",
    val cabinetType: String = "IR",
    val cabinetInGain: Float = 0f,
    val cabinetOutGain: Float = 0f,
    val cabinetMix: Float = 1f,
    val cabinetEqEnabled: Boolean = true,
    val cabinetEqPre: Boolean = false,
    val cabinetEq: List<Float> = List(6) { 0f },
    val modules: List<UiModule> = emptyList(),
    val presets: List<UiPreset> = emptyList(),
    val status: String = "Ready",
    val routing: String = "",
    val processingPercent: Float? = null,
    val processingAvgUs: Float? = null,
    val processingMaxUs: Float = 0f,
    val processingBudgetUs: Float = 0f,
    val overBudgetCount: Long = 0,
    val audioIoErrors: Long = 0,
)

internal fun readPicoloState(json: String, status: String, stats: String): PicoloUiState = try {
    val root = JSONObject(json)
    val modulesJson = root.optJSONArray("signalChain")
    val modules = buildList {
        if (modulesJson != null) for (i in 0 until modulesJson.length()) {
            val item = modulesJson.optJSONObject(i) ?: continue
            val type = item.optString("type", "NAM")
            add(UiModule(
                id = when (type) {
                    "CABINET_IR" -> "cabinet-ir"
                    "FX_NATIVE" -> "fxnative-${item.optInt("nativeIndex", i)}"
                    "FX" -> "fx-${item.optInt("fxIndex", i)}"
                    else -> "nam-${item.optInt("chainIndex", i)}"
                },
                type = type,
                name = if (type == "CABINET_IR") {
                    root.optString("cabinetIrName", item.optString("name", "Cabinet IR"))
                } else {
                    item.optString("name", item.optString("modelName", if (type == "FX") "Space FX" else "NAM module"))
                },
                moduleType = item.optString("moduleType", "AMP").uppercase(Locale.US),
                index = when (type) {
                    "FX" -> item.optInt("fxIndex", i)
                    "FX_NATIVE" -> item.optInt("nativeIndex", i)
                    else -> item.optInt("chainIndex", i)
                },
                bypass = if (type == "CABINET_IR") root.optBoolean("cabinetIrBypass", false) else item.optBoolean("bypass", false),
                gainDb = item.optDouble("gainDb", -15.0).toFloat(),
                inGainDb = item.optDouble("inGainDb", 0.0).toFloat(),
                mix = item.optDouble("mix", 1.0).toFloat(),
                eqEnabled = item.optBoolean("eqEnabled", true),
                eqPre = item.optBoolean("eqPre", false),
                normalize = item.optBoolean("normalize", true),
                a2Full = item.optBoolean("a2Full", false),
                eqBands = listOf("eqLowDb", "eqMidDb", "eqHighDb", "eqBand3Db", "eqBand4Db", "eqBand5Db")
                    .map { key -> item.optDouble(key, 0.0).toFloat() },
                nativeEffect = item.optInt("effect", 0),
                nativeParam1 = item.optDouble("param1", 350.0).toFloat(),
                nativeParam2 = item.optDouble("param2", 0.35).toFloat(),
                nativeParam3 = item.optDouble("param3", 12.0).toFloat(),
            ))
        }
    }
    val presetsJson = root.optJSONArray("presets")
    val presets = buildList {
        if (presetsJson != null) for (i in 0 until presetsJson.length()) {
            val item = presetsJson.optJSONObject(i) ?: continue
            add(UiPreset(item.optInt("slot", i + 1), item.optString("label", "Preset ${i + 1}"), item.optBoolean("saved", false)))
        }
    }
    val cabinetEqJson = root.optJSONArray("cabinetIrEq")
    val cabinetEq = List(6) { index -> cabinetEqJson?.optDouble(index, 0.0)?.toFloat() ?: 0f }
    fun peakDbFs(key: String): Float? = Regex("(?m)^$key=.*\\((-?[0-9]+(?:\\.[0-9]+)?) dBFS\\)")
        .find(stats)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    PicoloUiState(
        running = root.optBoolean("running"),
        inputDbFs = peakDbFs("capturePeak"), outputDbFs = peakDbFs("postEqPeak"),
        bypass = root.optBoolean("bypass"),
        modelName = root.optString("modelName", "No capture loaded"),
        toneTitle = root.optString("toneTitle", ""), activePresetSlot = root.optInt("activePresetSlot", 0),
        device = root.optString("audioDevice", "Audio interface: auto detect"),
        inputGain = root.optDouble("inputGain", 0.0).toFloat(),
        outputGain = root.optDouble("outputGain", 0.0).toFloat(),
        gateEnabled = root.optBoolean("gateEnabled"), gateThreshold = root.optDouble("gateThreshold", -65.0).toFloat(),
        eqEnabled = root.optBoolean("eqEnabled", true), eqLow = root.optDouble("eqLow", 0.0).toFloat(),
        eqMid = root.optDouble("eqMid", 0.0).toFloat(), eqHigh = root.optDouble("eqHigh", 0.0).toFloat(),
        cabinetLoaded = root.optBoolean("cabinetIrLoaded"), cabinetName = root.optString("cabinetIrName"),
        cabinetType = root.optString("cabinetIrModuleType", "IR"),
        cabinetInGain = root.optDouble("cabinetIrInGain", 0.0).toFloat(),
        cabinetOutGain = root.optDouble("cabinetIrOutGain", 0.0).toFloat(),
        cabinetMix = root.optDouble("cabinetIrMix", 1.0).toFloat(),
        cabinetEqEnabled = root.optBoolean("cabinetIrEqEnabled", true),
        cabinetEqPre = root.optBoolean("cabinetIrEqPre", false),
        cabinetEq = cabinetEq,
        modules = modules, presets = presets, status = status.ifBlank { "Ready" }, routing = root.optString("routing"),
    )
} catch (_: Exception) {
    PicoloUiState(status = status.ifBlank { "Ready" })
}
