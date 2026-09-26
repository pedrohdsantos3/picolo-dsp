package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.ActiveToneRepository
import com.pedro.tone3000m1.domain.repository.CurrentToneRepository
import java.io.File
import kotlinx.coroutines.flow.first

internal class CurrentToneRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : CurrentToneRepository, ActiveToneRepository {
    override suspend fun currentModelFile(): File? =
        preferences.data.first()[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_PATH)]?.let(::File)

    override suspend fun save(toneId: String, toneTitle: String, model: OnlineModel, moduleType: String, file: File) {
        preferences.edit { values ->
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_PATH)] = file.absolutePath
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_NAME)] = model.name
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_SIZE)] = model.size
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_TYPE)] = moduleType
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_TONE_ID)] = toneId
            values[stringPreferencesKey(PresetPreferenceKeys.LAST_TONE_TITLE)] = toneTitle
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_GAIN_DB)] = if (moduleType == "PEDAL") -10f else -15f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_IN_GAIN_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_MIX)] = 1f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_LOW_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_MID_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_HIGH_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_BAND3_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_BAND4_DB)] = 0f
            values[floatPreferencesKey(PresetPreferenceKeys.NAM_EQ_BAND5_DB)] = 0f
            values[booleanPreferencesKey(PresetPreferenceKeys.NAM_EQ_PRE)] = false
            values[booleanPreferencesKey(PresetPreferenceKeys.NAM_EQ_ENABLED)] = true
            values[booleanPreferencesKey(PresetPreferenceKeys.NAM_NORMALIZE)] = moduleType != "PEDAL"
            values[booleanPreferencesKey(PresetPreferenceKeys.NAM_A2_FULL)] = moduleType == "AMP"
        }
    }

    override suspend fun clear() {
        preferences.edit { values ->
            values.remove(stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_PATH))
            values.remove(stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_NAME))
            values.remove(stringPreferencesKey(PresetPreferenceKeys.LAST_MODEL_SIZE))
            values.remove(stringPreferencesKey(PresetPreferenceKeys.LAST_TONE_ID))
            values.remove(stringPreferencesKey(PresetPreferenceKeys.LAST_TONE_TITLE))
        }
    }
}
