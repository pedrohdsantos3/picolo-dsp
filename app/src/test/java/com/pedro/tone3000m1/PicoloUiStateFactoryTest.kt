package com.pedro.tone3000m1

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pedro.tone3000m1.data.repository.PresetPreferenceKeys
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.ui.model.PicoloUiStateFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PicoloUiStateFactoryTest {
    @Test
    fun buildsTypedOrderedStateWithoutJsonSnapshot() {
        val primary = File.createTempFile("primary", ".nam")
        val cabinet = File.createTempFile("cabinet", ".wav")
        val storeFile = File.createTempFile("picolo", ".preferences_pb").also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })
            val state = runBlocking {
                dataStore.edit { preferences ->
                    preferences[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_PATH)] = primary.absolutePath
                    preferences[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_NAME)] = "Primary NAM"
                    preferences[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_TYPE)] = "pedal"
                    preferences[floatPreferencesKey(PresetPreferenceKeys.NAM_GAIN_DB)] = -9f
                    preferences[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_BAND5_DB)] = 2f
                    preferences[stringPreferencesKey(PresetPreferenceKeys.CABINET_IR_PATH)] = cabinet.absolutePath
                    preferences[stringPreferencesKey(PresetPreferenceKeys.CABINET_IR_TITLE)] = "Cabinet"
                    preferences[intPreferencesKey(PresetPreferenceKeys.CABINET_IR_POSITION)] = 0
                    preferences[booleanPreferencesKey(PresetPreferenceKeys.CABINET_IR_BYPASS)] = true
                    preferences[floatPreferencesKey(PresetPreferenceKeys.INPUT_GAIN)] = 3f
                    preferences[intPreferencesKey(PresetPreferenceKeys.ACTIVE_PRESET_SLOT)] = 2
                }
                val factory = PicoloUiStateFactory(
                    preferenceDataStore = dataStore,
                    isRunning = { true },
                    readRouting = { "USB input 1 -> output 1/2" },
                    readAudioDevice = { "USB Audio" },
                    readExtras = {
                        listOf(
                            ExtraNamEntry(
                                toneId = "extra",
                                toneTitle = "Extra",
                                modelId = 0L,
                                modelName = "Second NAM",
                                size = "small",
                                path = "/second.nam",
                                bypass = false,
                            ),
                        )
                    },
                    readFxChain = { listOf(FxImpulseEntry(title = "Reverb", path = "/reverb.wav", position = 0)) },
                    readFxNativeChain = { listOf(FxNativeEntry(effect = 2, param1 = 1200f, param2 = 0.6f)) },
                    presetReader = { null },
                    presetLabeler = { "Preset $it" },
                    presetCount = 2,
                    maxNamBlocks = 4,
                )

                factory.build(bypass = false, status = "Ready")
            }

            assertTrue(state.running)
            assertEquals("Primary NAM", state.modelName)
            assertEquals("USB Audio", state.device)
            assertEquals(3f, state.inputGain)
            assertEquals(2, state.activePresetSlot)
            assertEquals(
                listOf("cabinet-ir", "fx-0", "nam-0", "nam-1", "fxnative-0"),
                state.modules.map { it.id },
            )
            assertEquals("PEDAL", state.modules[2].moduleType)
            assertEquals(-9f, state.modules[2].gainDb)
            assertEquals(2f, state.modules[2].eqBands[5])
            assertTrue(state.modules.first().bypass)
            assertEquals("BYOD Smooth Reverb", state.modules.last().name)
            assertEquals(listOf("Preset 1", "Preset 2"), state.presets.map { it.label })
            assertTrue(state.presets.none { it.saved })
            assertFalse(state.cabinetEqPre)
        } finally {
            primary.delete()
            cabinet.delete()
            storeFile.delete()
            scope.cancel()
        }
    }
}
