package com.pedro.tone3000m1.ui.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.domain.model.PresetData
import com.pedro.tone3000m1.domain.model.LocalNamLibraryItem
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Builds the immutable screen model directly from persisted and engine state. */
internal class PicoloUiStateFactory(
    private val preferenceDataStore: DataStore<Preferences>,
    private val isRunning: () -> Boolean,
    private val readRouting: () -> String,
    private val readAudioDevice: () -> String,
    private val readExtras: () -> List<ExtraNamEntry>,
    private val readFxChain: () -> List<FxImpulseEntry>,
    private val readFxNativeChain: () -> List<FxNativeEntry>,
    private val presetReader: (Int) -> PresetData?,
    private val presetLabeler: (Int) -> String,
    private val presetCount: Int,
    private val maxNamBlocks: Int,
    private val readLocalNamLibrary: () -> List<LocalNamLibraryItem> = { emptyList() },
) {
    suspend fun build(
        bypass: Boolean,
        status: String,
        loadingMessage: String? = null,
    ): PicoloUiState {
        val preferences = PreferenceValues(preferenceDataStore.data.first())
        val primaryPath = preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null)
        val hasPrimary = primaryPath != null && File(primaryPath).exists()
        val primaryType = preferences.getString(PresetPreferenceKeys.LAST_MODEL_TYPE, "AMP")
            ?.uppercase(Locale.US) ?: "AMP"
        val extraNamEntries = readExtras()
        val namModules = buildList {
            if (hasPrimary) {
                add(
                    UiModule(
                        id = "nam-0",
                        type = TYPE_NAM,
                        name = preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, "No capture loaded")
                            ?: "No capture loaded",
                        moduleType = primaryType,
                        index = 0,
                        bypass = bypass,
                        gainDb = preferences.getFloat(PresetPreferenceKeys.NAM_GAIN_DB, -15f),
                        inGainDb = preferences.getFloat(PresetPreferenceKeys.NAM_IN_GAIN_DB, 0f),
                        mix = preferences.getFloat(PresetPreferenceKeys.NAM_MIX, 1f),
                        eqEnabled = preferences.getBoolean(PresetPreferenceKeys.NAM_EQ_ENABLED, true),
                        eqPre = preferences.getBoolean(PresetPreferenceKeys.NAM_EQ_PRE, false),
                        normalize = preferences.getBoolean(PresetPreferenceKeys.NAM_NORMALIZE, true),
                        a2Full = preferences.getBoolean(PresetPreferenceKeys.NAM_A2_FULL, false),
                        eqBands = namEqBands(preferences),
                    ),
                )
            }
            extraNamEntries.forEachIndexed { index, entry ->
                val chainIndex = index + if (hasPrimary) 1 else 0
                add(entry.toUiModule(chainIndex))
            }
        }

        val cabinetPath = preferences.getString(PresetPreferenceKeys.CABINET_IR_PATH, null)
        val cabinetLoaded = cabinetPath != null && File(cabinetPath).exists()
        val cabinetName = preferences.getString(
            PresetPreferenceKeys.CABINET_IR_TITLE,
            cabinetPath?.let { File(it).nameWithoutExtension } ?: "",
        ) ?: ""
        val cabinetPosition = preferences.getInt(PresetPreferenceKeys.CABINET_IR_POSITION, maxNamBlocks)
            .coerceIn(0, namModules.size)
        val fx = readFxChain()
        val fxNative = readFxNativeChain()
        val nativePositions = fxNative.map { entry ->
            val postChain = entry.position > namModules.size ||
                (entry.position == namModules.size && (!cabinetLoaded || cabinetPosition < entry.position))
            if (postChain) maxNamBlocks + 1 else entry.position
        }
        val modules = buildList {
            for (position in 0..namModules.size) {
                fxNative.forEachIndexed { index, entry ->
                    if (nativePositions[index] == position) {
                        add(entry.toUiModule(index, nativePositions[index]))
                    }
                }
                if (cabinetLoaded && cabinetPosition == position) {
                    add(
                        UiModule(
                            id = CABINET_ID,
                            type = TYPE_CABINET,
                            name = cabinetName,
                            index = position,
                            bypass = preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_BYPASS, false),
                        ),
                    )
                }
                fx.forEachIndexed { index, entry ->
                    if (entry.position == position) add(entry.toUiModule(index))
                }
                if (position < namModules.size) add(namModules[position])
            }
            fxNative.forEachIndexed { index, entry ->
                if (nativePositions[index] > namModules.size) {
                    add(entry.toUiModule(index, nativePositions[index]))
                }
            }
        }

        return PicoloUiState(
            running = isRunning(),
            bypass = bypass,
            modelName = preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, "No capture loaded")
                ?: "No capture loaded",
            toneTitle = preferences.getString(PresetPreferenceKeys.LAST_TONE_TITLE, "") ?: "",
            activePresetSlot = preferences.getInt(PresetPreferenceKeys.ACTIVE_PRESET_SLOT, 0),
            globalTapTempoBpm = preferences.getFloat(PresetPreferenceKeys.GLOBAL_TAP_TEMPO_BPM, 120f).coerceIn(40f, 240f),
            device = readAudioDevice(),
            inputGain = preferences.getFloat(PresetPreferenceKeys.INPUT_GAIN, 0f),
            outputGain = preferences.getFloat(PresetPreferenceKeys.OUTPUT_GAIN, -10f),
            gateEnabled = preferences.getBoolean(PresetPreferenceKeys.GATE_ENABLED, false),
            gateThreshold = preferences.getFloat(PresetPreferenceKeys.GATE_THRESHOLD, -65f),
            eqEnabled = preferences.getBoolean(PresetPreferenceKeys.EQ_ENABLED, true),
            eqLow = preferences.getFloat(PresetPreferenceKeys.EQ_LOW, 0f),
            eqMid = preferences.getFloat(PresetPreferenceKeys.EQ_MID, 0f),
            eqHigh = preferences.getFloat(PresetPreferenceKeys.EQ_HIGH, 0f),
            cabinetLoaded = cabinetLoaded,
            cabinetName = cabinetName,
            cabinetType = preferences.getString(PresetPreferenceKeys.CABINET_IR_TYPE, "IR") ?: "IR",
            cabinetInGain = preferences.getFloat(PresetPreferenceKeys.CABINET_IR_IN_GAIN, 0f),
            cabinetOutGain = preferences.getFloat(PresetPreferenceKeys.CABINET_IR_OUT_GAIN, 0f),
            cabinetMix = preferences.getFloat(PresetPreferenceKeys.CABINET_IR_MIX, 1f),
            cabinetEqEnabled = preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_EQ_ENABLED, true),
            cabinetEqPre = preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_EQ_PRE, false),
            cabinetEq = List(6) { band ->
                preferences.getFloat(PresetPreferenceKeys.CABINET_IR_EQ_PREFIX + band, 0f)
            },
            modules = modules,
            presets = (1..presetCount).map { slot ->
                UiPreset(slot, presetLabeler(slot), presetReader(slot) != null)
            },
            localNamCaptures = buildList {
                readLocalNamLibrary().forEach { capture ->
                    add(
                        UiLocalNamCapture(
                            path = capture.file.absolutePath,
                            modelName = capture.modelName,
                            modelSize = capture.modelSize,
                            toneTitle = capture.toneTitle,
                            toneId = capture.toneId,
                            imageUrl = capture.imageUrl,
                            moduleType = capture.moduleType.uppercase(Locale.US),
                            modelId = capture.modelId,
                        ),
                    )
                }
                if (hasPrimary) {
                    add(
                        UiLocalNamCapture(
                            path = primaryPath,
                            modelName = preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, File(primaryPath).name)
                                ?: File(primaryPath).name,
                            modelSize = preferences.getString(PresetPreferenceKeys.LAST_MODEL_SIZE, "unknown") ?: "unknown",
                            toneTitle = preferences.getString(PresetPreferenceKeys.LAST_TONE_TITLE, "") ?: "",
                            toneId = preferences.getString(PresetPreferenceKeys.LAST_TONE_ID, "") ?: "",
                            imageUrl = preferences.getString(PresetPreferenceKeys.LAST_TONE_IMAGE, "") ?: "",
                            moduleType = primaryType,
                        ),
                    )
                }
                extraNamEntries.forEach { entry ->
                    if (File(entry.path).exists()) {
                        add(
                            UiLocalNamCapture(
                                path = entry.path,
                                modelName = entry.modelName,
                                modelSize = entry.size,
                                toneTitle = entry.toneTitle,
                                toneId = entry.toneId,
                                imageUrl = entry.imageUrl,
                                moduleType = entry.moduleType.uppercase(Locale.US),
                                modelId = entry.modelId,
                            ),
                        )
                    }
                }
                (1..presetCount).mapNotNull(presetReader).forEach { preset ->
                    add(
                        UiLocalNamCapture(
                            path = preset.modelPath,
                            modelName = preset.modelName,
                            modelSize = preset.modelSize,
                            toneTitle = preset.toneTitle.orEmpty(),
                            toneId = preset.toneId.orEmpty(),
                            imageUrl = "",
                            moduleType = preset.moduleType.uppercase(Locale.US),
                        ),
                    )
                }
            }.distinctBy { it.path }.filter { File(it.path).exists() },
            selectedModuleType = preferences.getString(PresetPreferenceKeys.SELECTED_ADD_TYPE, "AMP")
                ?.uppercase(Locale.US) ?: "AMP",
            status = status.ifBlank { "Ready" },
            loadingMessage = loadingMessage,
            routing = readRouting(),
        )
    }

    private fun namEqBands(preferences: PreferenceValues): List<Float> = listOf(
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_LOW_DB, 0f),
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_MID_DB, 0f),
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_HIGH_DB, 0f),
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_BAND3_DB, 0f),
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_BAND4_DB, 0f),
        preferences.getFloat(PresetPreferenceKeys.NAM_EQ_BAND5_DB, 0f),
    )

    private fun ExtraNamEntry.toUiModule(chainIndex: Int) = UiModule(
        id = "nam-$chainIndex",
        type = TYPE_NAM,
        name = modelName,
        moduleType = moduleType.uppercase(Locale.US),
        index = chainIndex,
        bypass = bypass,
        gainDb = gainDb,
        inGainDb = inGainDb,
        mix = mix,
        eqEnabled = eqEnabled,
        eqPre = eqPre,
        normalize = normalize,
        a2Full = a2Full,
        eqBands = listOf(eqLowDb, eqMidDb, eqHighDb, eqBand3Db, eqBand4Db, eqBand5Db),
    )

    private fun FxImpulseEntry.toUiModule(index: Int) = UiModule(
        id = "fx-$index",
        type = TYPE_FX,
        name = title,
        index = index,
        bypass = bypass,
        mix = mix,
    )

    private fun FxNativeEntry.toUiModule(index: Int, uiPosition: Int): UiModule {
        val effect = effect.coerceIn(0, NATIVE_FX_NAMES.lastIndex)
        return UiModule(
            id = "fxnative-$index",
            type = TYPE_FX_NATIVE,
            name = NATIVE_FX_NAMES[effect],
            index = index,
            bypass = bypass,
            mix = mix,
            nativeEffect = effect,
            nativePosition = uiPosition,
            nativeParam1 = param1,
            nativeParam2 = param2,
            nativeParam3 = param3,
            nativeOutputGainDb = outputGainDb,
            fxTempoSync = tempoSync,
            fxSyncLeftNote = leftNote,
            fxSyncRightNote = rightNote,
        )
    }

    private companion object {
        const val TYPE_NAM = "NAM"
        const val TYPE_FX = "FX"
        const val TYPE_FX_NATIVE = "FX_NATIVE"
        const val TYPE_CABINET = "CABINET_IR"
        const val CABINET_ID = "cabinet-ir"
        val NATIVE_FX_NAMES = listOf(
            "ChowMatrix Delay",
            "BYOD BBD Delay",
            "BYOD Smooth Reverb",
            "BYOD Shimmer Reverb",
            "Spring Reverb",
            "Ping-Pong Delay",
            "Plate Reverb",
            "Airwindows kPlate140",
            "MVerb Reverb",
            "Airwindows Tape Delay 2",
            "Dual Delay",
            "Chorus",
        )
    }
}

private class PreferenceValues(private val values: Preferences) {
    fun getString(key: String, default: String?): String? = values[stringPreferencesKey(key)] ?: default
    fun getInt(key: String, default: Int): Int = values[intPreferencesKey(key)] ?: default
    fun getFloat(key: String, default: Float): Float = values[floatPreferencesKey(key)] ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = values[booleanPreferencesKey(key)] ?: default
}
