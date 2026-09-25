package com.pedro.tone3000m1.ui.model

import android.content.SharedPreferences
import com.pedro.tone3000m1.MainActivity
import com.pedro.tone3000m1.NativeAudioEngine
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.domain.model.PresetData
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Builds the legacy JSON payload consumed by the existing UI state decoder. */
internal class PicoloLegacyStateJsonFactory(
    private val preferences: SharedPreferences,
    private val engine: NativeAudioEngine,
    private val readExtras: () -> List<ExtraNamEntry>,
    private val readFxChain: () -> List<FxImpulseEntry>,
    private val readFxNativeChain: () -> List<FxNativeEntry>,
    private val presetReader: (Int) -> PresetData?,
    private val presetLabeler: (Int) -> String,
) {
    fun build(bypass: Boolean): String {

        val result =
            JSONObject()


        result.put(
            "running",
            engine.nativeIsRunning()
        )

        result.put(
            "bypass",
            bypass
        )

        result.put(
            "modelName",
            preferences.getString(
                MainActivity.PREF_LAST_MODEL_NAME,
                "No capture loaded"
            )
        )
        result.put("activePresetSlot", preferences.getInt(MainActivity.PREF_ACTIVE_PRESET_SLOT, 0))

        result.put(
            "modelSize",
            preferences.getString(
                MainActivity.PREF_LAST_MODEL_SIZE,
                ""
            )
        )

        result.put(
            "toneTitle",
            preferences.getString(
                MainActivity.PREF_LAST_TONE_TITLE,
                ""
            )
        )

        val cabinetPath = preferences.getString(MainActivity.PREF_CABINET_IR_PATH, null)
        result.put("cabinetIrLoaded", cabinetPath != null && File(cabinetPath).exists())
        result.put("cabinetIrName", preferences.getString(MainActivity.PREF_CABINET_IR_TITLE, cabinetPath?.let { File(it).nameWithoutExtension } ?: "") ?: "")
        result.put("cabinetIrModuleType", preferences.getString(MainActivity.PREF_CABINET_IR_TYPE, "IR") ?: "IR")
        result.put("cabinetIrLong", preferences.getString(MainActivity.PREF_CABINET_IR_TYPE, "IR") == "FX")
        result.put("cabinetIrImage", preferences.getString(MainActivity.PREF_CABINET_IR_IMAGE, ""))
        result.put("cabinetIrBypass", preferences.getBoolean(MainActivity.PREF_CABINET_IR_BYPASS, false))
        result.put("cabinetIrPosition", preferences.getInt(MainActivity.PREF_CABINET_IR_POSITION, MainActivity.MAX_NAM_BLOCKS))
        result.put("cabinetIrInGain", preferences.getFloat(MainActivity.PREF_CABINET_IR_IN_GAIN, 0.0f).toDouble())
        result.put("cabinetIrOutGain", preferences.getFloat(MainActivity.PREF_CABINET_IR_OUT_GAIN, 0.0f).toDouble())
        result.put("cabinetIrMix", preferences.getFloat(MainActivity.PREF_CABINET_IR_MIX, 1.0f).toDouble())
        result.put("cabinetIrEqPre", preferences.getBoolean(MainActivity.PREF_CABINET_IR_EQ_PRE, false))
        result.put("cabinetIrEqEnabled", preferences.getBoolean(MainActivity.PREF_CABINET_IR_EQ_ENABLED, true))
        val cabinetEq = JSONArray()
        for (band in 0 until 6) cabinetEq.put(preferences.getFloat(MainActivity.PREF_CABINET_IR_EQ_PREFIX + band, 0.0f).toDouble())
        result.put("cabinetIrEq", cabinetEq)

        result.put(
            "inputGain",
            preferences.getFloat(
                MainActivity.PREF_INPUT_GAIN,
                0.0f
            ).toDouble()
        )

        result.put(
            "outputGain",
            preferences.getFloat(
                MainActivity.PREF_OUTPUT_GAIN,
                -10.0f
            ).toDouble()
        )

        result.put(
            "inputChannel",
            preferences.getInt(
                MainActivity.PREF_INPUT_CHANNEL,
                0
            )
        )

        result.put(
            "outputPair",
            preferences.getInt(
                MainActivity.PREF_OUTPUT_PAIR,
                0
            )
        )

        result.put(
            "gateEnabled",
            preferences.getBoolean(
                MainActivity.PREF_GATE_ENABLED,
                false
            )
        )

        result.put(
            "gateThreshold",
            preferences.getFloat(
                MainActivity.PREF_GATE_THRESHOLD,
                -65.0f
            ).toDouble()
        )

        result.put(
            "eqLow",
            preferences.getFloat(
                MainActivity.PREF_EQ_LOW,
                0.0f
            ).toDouble()
        )

        result.put(
            "eqMid",
            preferences.getFloat(
                MainActivity.PREF_EQ_MID,
                0.0f
            ).toDouble()
        )

        result.put(
            "eqHigh",
            preferences.getFloat(
                MainActivity.PREF_EQ_HIGH,
                0.0f
            ).toDouble()
        )

        result.put("eqEnabled", preferences.getBoolean(MainActivity.PREF_EQ_ENABLED, true))

        result.put(
            "routing",
            engine.nativeGetRoutingInfo()
        )

        result.put(
            "audioDevice",
            engine.nativeGetAudioDeviceInfo()
        )

        result.put(
            "namBlockCount",
            engine.nativeGetNamBlockCount()
        )


        val namChain =
            JSONArray()


        val firstModelPath = preferences.getString(MainActivity.PREF_LAST_MODEL_PATH, null)
        val hasFirstModel = firstModelPath != null && File(firstModelPath).exists()
        if (hasFirstModel) {
            namChain.put(
                JSONObject()
                .put(
                    "chainIndex",
                    0
                )
                .put(
                    "modelName",
                    preferences.getString(
                        MainActivity.PREF_LAST_MODEL_NAME,
                        "No capture loaded"
                    )
                )
                .put(
                    "toneTitle",
                    preferences.getString(
                        MainActivity.PREF_LAST_TONE_TITLE,
                        ""
                    )
                )
                .put(
                    "size",
                    preferences.getString(
                        MainActivity.PREF_LAST_MODEL_SIZE,
                        ""
                    )
                )
                .put(
                    "bypass",
                    bypass
                )
                .put("gainDb", preferences.getFloat(MainActivity.PREF_NAM_GAIN_DB, -15.0f))
                .put("inGainDb", preferences.getFloat(MainActivity.PREF_NAM_IN_GAIN_DB, 0.0f))
                .put("mix", preferences.getFloat(MainActivity.PREF_NAM_MIX, 1.0f))
                .put("eqLowDb", preferences.getFloat(MainActivity.PREF_NAM_EQ_LOW_DB, 0.0f))
                .put("eqMidDb", preferences.getFloat(MainActivity.PREF_NAM_EQ_MID_DB, 0.0f))
                .put("eqHighDb", preferences.getFloat(MainActivity.PREF_NAM_EQ_HIGH_DB, 0.0f))
                .put("eqBand3Db", preferences.getFloat(MainActivity.PREF_NAM_EQ_BAND3_DB, 0.0f))
                .put("eqBand4Db", preferences.getFloat(MainActivity.PREF_NAM_EQ_BAND4_DB, 0.0f))
                .put("eqBand5Db", preferences.getFloat(MainActivity.PREF_NAM_EQ_BAND5_DB, 0.0f))
                .put("eqPre", preferences.getBoolean(MainActivity.PREF_NAM_EQ_PRE, false))
                .put("eqEnabled", preferences.getBoolean(MainActivity.PREF_NAM_EQ_ENABLED, true))
                .put("normalize", preferences.getBoolean(MainActivity.PREF_NAM_NORMALIZE, true))
                .put("a2Full", preferences.getBoolean(MainActivity.PREF_NAM_A2_FULL, false))
                .put("moduleType", preferences.getString(MainActivity.PREF_LAST_MODEL_TYPE, "AMP"))
                .put("images", JSONArray().put(preferences.getString(MainActivity.PREF_LAST_TONE_IMAGE, "")))
            )
        }


        readExtras()
            .forEachIndexed { index, entry ->

                namChain.put(
                    JSONObject()
                        .put(
                            "chainIndex",
                            index + if (hasFirstModel) 1 else 0
                        )
                        .put(
                            "modelName",
                            entry.modelName
                        )
                        .put(
                            "toneTitle",
                            entry.toneTitle
                        )
                        .put(
                            "size",
                            entry.size
                        )
                        .put(
                            "bypass",
                            entry.bypass
                        )
                        .put("gainDb", entry.gainDb)
                        .put("inGainDb", entry.inGainDb)
                        .put("mix", entry.mix)
                        .put("eqLowDb", entry.eqLowDb)
                        .put("eqMidDb", entry.eqMidDb)
                        .put("eqHighDb", entry.eqHighDb)
                        .put("eqBand3Db", entry.eqBand3Db)
                        .put("eqBand4Db", entry.eqBand4Db)
                        .put("eqBand5Db", entry.eqBand5Db)
                        .put("eqPre", entry.eqPre)
                        .put("eqEnabled", entry.eqEnabled)
                        .put("normalize", entry.normalize)
                        .put("a2Full", entry.a2Full)
                        .put("moduleType", entry.moduleType)
                        .put("images", JSONArray().put(entry.imageUrl))
                )
            }


        result.put(
            "namChain",
            namChain
        )

        val signalChain = JSONArray()
        val fxChain = readFxChain()
        val fxNativeChain = readFxNativeChain()
        result.put("fxChain", JSONArray().also { array -> fxChain.forEach { array.put(it.toUiJson()) } })
        result.put("fxNativeChain", JSONArray().also { array -> fxNativeChain.forEach { array.put(it.toUiJson()) } })
        val irLoaded = cabinetPath != null && File(cabinetPath).exists()
        val irPosition = preferences.getInt(MainActivity.PREF_CABINET_IR_POSITION, MainActivity.MAX_NAM_BLOCKS)
            .coerceIn(0, namChain.length())
        for (position in 0..namChain.length()) {
            if (irLoaded && irPosition == position) {
                signalChain.put(JSONObject()
                    .put("type", "CABINET_IR")
                    .put("position", position)
                    .put("name", File(cabinetPath).name))
            }
            fxChain.forEachIndexed { index, fx ->
                if (fx.position == position) {
                    signalChain.put(JSONObject()
                        .put("type", "FX")
                        .put("fxIndex", index)
                        .put("position", position)
                        .put("name", fx.title)
                        .put("bypass", fx.bypass)
                        .put("mix", fx.mix.toDouble()))
                }
            }
            if (position < namChain.length()) signalChain.put(namChain.getJSONObject(position).put("type", "NAM"))
        }
        // FXNative is intentionally a stereo post section: it cannot be
        // inserted before a NAM or either cabinet/space convolution stage.
        fxNativeChain.forEachIndexed { index, item ->
            val effect = item.effect.coerceIn(0, 3)
            val names = arrayOf("ChowMatrix Delay", "BYOD BBD Delay", "BYOD Smooth Reverb", "BYOD Shimmer Reverb")
            signalChain.put(JSONObject()
                .put("type", "FX_NATIVE")
                .put("nativeIndex", index)
                .put("effect", effect)
                .put("position", MainActivity.MAX_NAM_BLOCKS)
                .put("name", names[effect])
                .put("bypass", item.bypass)
                .put("mix", item.mix.toDouble())
                .put("param1", item.param1.toDouble())
                .put("param2", item.param2.toDouble())
                .put("param3", item.param3.toDouble()))
        }
        result.put("signalChain", signalChain)


        val presets =
            JSONArray()


        for (
        slot in
        1..MainActivity.PRESET_COUNT
        ) {

            val preset =
                presetReader(
                    slot
                )


            val item =
                JSONObject()


            item.put(
                "slot",
                slot
            )

            item.put(
                "saved",
                preset != null
            )

            item.put(
                "label",
                presetLabeler(
                    slot
                )
            )


            if (preset != null) {

                item.put(
                    "modelName",
                    preset.modelName
                )

                item.put(
                    "toneTitle",
                    preset.toneTitle
                        ?: ""
                )
            }


            presets.put(
                item
            )
        }


        result.put(
            "presets",
            presets
        )


        return result.toString()
    }


    private fun FxImpulseEntry.toUiJson() = JSONObject()
        .put("toneId", toneId).put("title", title).put("image", image)
        .put("modelId", modelId).put("modelName", modelName).put("path", path)
        .put("bypass", bypass).put("mix", mix).put("position", position)

    private fun FxNativeEntry.toUiJson() = JSONObject()
        .put("effect", effect).put("bypass", bypass).put("mix", mix)
        .put("param1", param1).put("param2", param2).put("param3", param3)
}
