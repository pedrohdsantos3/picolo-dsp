package com.pedro.tone3000m1.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import org.json.JSONObject
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataRepositoryCompatibilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var preferenceName: String
    private lateinit var directory: File

    @Before fun setUp() {
        preferenceName = "preset-repository-test-${UUID.randomUUID()}"
        directory = File(context.cacheDir, preferenceName).apply { mkdirs() }
    }

    @After fun tearDown() {
        context.getSharedPreferences(preferenceName, 0).edit().clear().commit()
        context.deleteSharedPreferences(preferenceName)
        directory.deleteRecursively()
    }

    @Test fun saveCurrentCopiesModelAndKeepsLegacyPreferencesInSync() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val source = File(directory, "current.nam").apply { writeText("nam model bytes") }
        preferences.edit()
            .putString(PresetPreferenceKeys.LAST_MODEL_PATH, source.absolutePath)
            .putString(PresetPreferenceKeys.LAST_MODEL_NAME, "Current model")
            .putString(PresetPreferenceKeys.LAST_MODEL_SIZE, "2 MB")
            .putString(PresetPreferenceKeys.LAST_TONE_ID, "tone-42")
            .putString(PresetPreferenceKeys.LAST_TONE_TITLE, "Vintage amp")
            .putFloat(PresetPreferenceKeys.INPUT_GAIN, 3.5f)
            .putFloat(PresetPreferenceKeys.OUTPUT_GAIN, -1.5f)
            .commit()
        val repository = PresetRepositoryImpl(preferences, directory, presetCount = 4, maxNamBlocks = 6)

        repository.saveCurrent(slot = 2, namBypassFallback = false)

        val saved = repository.read(2)
        assertNotNull(saved)
        assertEquals("Current model", saved!!.modelName)
        assertEquals("tone-42", saved.toneId)
        assertEquals(3.5f, saved.inputGainDb)
        assertEquals(-1.5f, saved.outputGainDb)
        assertEquals("nam model bytes", File(saved.modelPath).readText())
        assertEquals(2, preferences.getInt(PresetPreferenceKeys.ACTIVE_PRESET_SLOT, -1))
        assertEquals(source.absolutePath, preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null))
        assertEquals(3.5f, preferences.getFloat(PresetPreferenceKeys.INPUT_GAIN, 0f))
    }

    @Test fun namChainRepositoryRoundTripsLegacyJsonAndFiltersInvalidPaths() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val primary = File(directory, "primary.nam").apply { writeText("primary") }
        val extra = File(directory, "extra.nam").apply { writeText("extra") }
        val repository = NamChainRepository(preferences, "extra_nam_chain_json", PresetPreferenceKeys.LAST_MODEL_PATH)
        preferences.edit().putString(PresetPreferenceKeys.LAST_MODEL_PATH, primary.absolutePath).commit()
        val entry = ExtraNamEntry(
            toneId = "tone", toneTitle = "Amp", modelId = 7L, modelName = "Extra", size = "small",
            path = extra.absolutePath, bypass = true, gainDb = -12f, moduleType = "PEDAL",
        )

        repository.persistExtraNamChain(listOf(entry, entry.copy(path = primary.absolutePath)))

        assertEquals(listOf(entry), repository.readExtraNamChain())
        val persisted = preferences.getString("extra_nam_chain_json", null)
        assertNotNull(persisted)
        assertTrue(persisted!!.contains("\"modelId\":7"))
    }

    @Test fun fxChainRepositoryKeepsLegacyJsonAndIgnoresMissingImpulseFiles() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val validIr = File(directory, "valid.wav").apply { writeText("wav") }
        val missingIr = File(directory, "missing.wav")
        val fx = JSONObject().put("path", validIr.absolutePath).put("mix", 0.5)
        val missing = JSONObject().put("path", missingIr.absolutePath)
        val repository = FxChainRepository(preferences, "fx_ir_chain_json", "fx_native_chain_json")
        preferences.edit().putString("fx_ir_chain_json", org.json.JSONArray().put(fx).put(missing).toString()).commit()

        val read = repository.readImpulseChain()
        repository.persistImpulseChain(read)

        assertEquals(1, read.size)
        assertEquals(validIr.absolutePath, read.single().path)
        assertTrue(preferences.getString("fx_ir_chain_json", "")!!.contains("\"mix\":0.5"))

        val nativeEntry = FxNativeEntry(effect = 2, mix = 0.7f, param1 = 1800f, param2 = 0.6f)
        repository.persistNativeChain(listOf(nativeEntry))
        assertEquals(listOf(nativeEntry), repository.readNativeChain())
        assertTrue(preferences.getString("fx_native_chain_json", "")!!.contains("\"effect\":2"))
    }

    @Test fun currentToneRepositoryReadsTheLegacyActiveModelPath() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val model = File(directory, "active.nam").apply { writeText("active model") }
        preferences.edit().putString(PresetPreferenceKeys.LAST_MODEL_PATH, model.absolutePath).commit()

        val currentTone = CurrentToneRepositoryImpl(preferences)
            .currentModelFile()

        assertEquals(model.absolutePath, currentTone?.absolutePath)
    }

    @Test fun cabinetImpulseRepositoryWritesLegacyKeysAndDefaults() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val ir = File(directory, "cabinet.wav").apply { writeText("wav") }

        CabinetImpulseRepositoryImpl(preferences).save(
            path = ir,
            imageUrl = "image",
            title = "Cabinet",
            toneId = "tone-77",
            moduleType = "FX",
            position = 3,
            mix = 0.5f,
        )

        assertEquals(ir.absolutePath, preferences.getString(PresetPreferenceKeys.CABINET_IR_PATH, null))
        assertEquals("image", preferences.getString("cabinet_ir_image", null))
        assertEquals("Cabinet", preferences.getString(PresetPreferenceKeys.CABINET_IR_TITLE, null))
        assertEquals("tone-77", preferences.getString(PresetPreferenceKeys.CABINET_IR_TONE_ID, null))
        assertEquals("FX", preferences.getString(PresetPreferenceKeys.CABINET_IR_TYPE, null))
        assertEquals(3, preferences.getInt(PresetPreferenceKeys.CABINET_IR_POSITION, -1))
        assertFalse(preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_BYPASS, true))
        assertEquals(0f, preferences.getFloat(PresetPreferenceKeys.CABINET_IR_IN_GAIN, -1f))
        assertEquals(0f, preferences.getFloat(PresetPreferenceKeys.CABINET_IR_OUT_GAIN, -1f))
        assertEquals(0.5f, preferences.getFloat(PresetPreferenceKeys.CABINET_IR_MIX, -1f))
        assertFalse(preferences.getBoolean(PresetPreferenceKeys.CABINET_IR_EQ_PRE, true))
        assertTrue(preferences.getBoolean("cabinet_ir_eq_enabled", false))
        for (band in 0 until 6) assertEquals(0f, preferences.getFloat(PresetPreferenceKeys.CABINET_IR_EQ_PREFIX + band, -1f))
    }

    @Test fun activeToneRepositorySavesLegacyModelFieldsAndPedalDefaults() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val model = File(directory, "pedal.nam").apply { writeText("pedal") }

        CurrentToneRepositoryImpl(preferences).save(
            toneId = "tone-pedal",
            toneTitle = "Pedal tone",
            model = OnlineModel(21, "Pedal capture", "small", "url"),
            moduleType = "PEDAL",
            file = model,
        )

        assertEquals(model.absolutePath, preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null))
        assertEquals("Pedal capture", preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, null))
        assertEquals("tone-pedal", preferences.getString(PresetPreferenceKeys.LAST_TONE_ID, null))
        assertEquals("PEDAL", preferences.getString(PresetPreferenceKeys.LAST_MODEL_TYPE, null))
        assertEquals(-10f, preferences.getFloat(PresetPreferenceKeys.NAM_GAIN_DB, 0f))
        assertFalse(preferences.getBoolean(PresetPreferenceKeys.NAM_NORMALIZE, true))
        assertFalse(preferences.getBoolean(PresetPreferenceKeys.NAM_A2_FULL, true))
    }

    @Test fun activeToneRepositoryClearPreservesTheExistingRemovalContract() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        preferences.edit()
            .putString(PresetPreferenceKeys.LAST_MODEL_PATH, "/active.nam")
            .putString(PresetPreferenceKeys.LAST_MODEL_NAME, "Active")
            .putString(PresetPreferenceKeys.LAST_MODEL_SIZE, "small")
            .putString(PresetPreferenceKeys.LAST_TONE_ID, "tone")
            .putString(PresetPreferenceKeys.LAST_TONE_TITLE, "Title")
            .putString(PresetPreferenceKeys.LAST_MODEL_TYPE, "AMP")
            .putFloat(PresetPreferenceKeys.NAM_GAIN_DB, -15f)
            .commit()

        CurrentToneRepositoryImpl(preferences).clear()

        assertEquals(null, preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null))
        assertEquals(null, preferences.getString(PresetPreferenceKeys.LAST_MODEL_NAME, null))
        assertEquals(null, preferences.getString(PresetPreferenceKeys.LAST_MODEL_SIZE, null))
        assertEquals(null, preferences.getString(PresetPreferenceKeys.LAST_TONE_ID, null))
        assertEquals(null, preferences.getString(PresetPreferenceKeys.LAST_TONE_TITLE, null))
        assertEquals("AMP", preferences.getString(PresetPreferenceKeys.LAST_MODEL_TYPE, null))
        assertEquals(-15f, preferences.getFloat(PresetPreferenceKeys.NAM_GAIN_DB, 0f))
    }

    @Test fun packageCaptureCacheKeepsExistingPreferenceKeyAndDeduplicatesModels() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val repository = TonePackageCaptureRepositoryImpl(preferences)
        val models = listOf(
            OnlineModel(10, "first", "small", "first-url"),
            OnlineModel(10, "duplicate", "large", "duplicate-url"),
            OnlineModel(11, "second", "medium", "second-url"),
        )

        repository.save("tone-7", "amp", models)

        assertTrue(preferences.contains("package_capture_cache_NAM_tone-7"))
        assertEquals(listOf(10L, 11L), repository.read("tone-7", "PEDAL").map { it.id })
        assertEquals("first", repository.read("tone-7", "NAM").first().name)
        assertEquals(emptyList<OnlineModel>(), repository.read("tone-7", "FX"))
    }

    @Test fun readSupportsExistingSavedPresetPreferenceFormat() {
        val preferences = context.getSharedPreferences(preferenceName, 0)
        val model = File(directory, "legacy.nam").apply { writeText("legacy model") }
        preferences.edit()
            .putBoolean("preset_1_saved", true)
            .putString("preset_1_model_path", model.absolutePath)
            .putString("preset_1_model_name", "Legacy preset")
            .putString("preset_1_model_size", "legacy size")
            .putFloat("preset_1_input_gain_db", 4f)
            .commit()
        val repository = PresetRepositoryImpl(preferences, directory, presetCount = 4, maxNamBlocks = 6)

        val preset = repository.read(1)

        assertNotNull(preset)
        assertEquals("Legacy preset", preset!!.modelName)
        assertEquals(4f, preset.inputGainDb)
        assertEquals(model.absolutePath, preset.modelPath)
    }
}
