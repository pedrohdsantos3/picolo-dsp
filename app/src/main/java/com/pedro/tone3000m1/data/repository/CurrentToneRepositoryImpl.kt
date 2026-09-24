package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.ActiveToneRepository
import com.pedro.tone3000m1.domain.repository.CurrentToneRepository
import java.io.File

internal class CurrentToneRepositoryImpl(
    private val preferences: SharedPreferences,
) : CurrentToneRepository, ActiveToneRepository {
    override fun currentModelFile(): File? =
        preferences.getString(PresetPreferenceKeys.LAST_MODEL_PATH, null)?.let(::File)

    override fun save(toneId: String, toneTitle: String, model: OnlineModel, moduleType: String, file: File) {
        preferences.edit()
            .putString(PresetPreferenceKeys.LAST_MODEL_PATH, file.absolutePath)
            .putString(PresetPreferenceKeys.LAST_MODEL_NAME, model.name)
            .putString(PresetPreferenceKeys.LAST_MODEL_SIZE, model.size)
            .putString(PresetPreferenceKeys.LAST_MODEL_TYPE, moduleType)
            .putString(PresetPreferenceKeys.LAST_TONE_ID, toneId)
            .putString(PresetPreferenceKeys.LAST_TONE_TITLE, toneTitle)
            .putFloat(PresetPreferenceKeys.NAM_GAIN_DB, if (moduleType == "PEDAL") -10f else -15f)
            .putFloat(PresetPreferenceKeys.NAM_IN_GAIN_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_MIX, 1f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_LOW_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_MID_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_HIGH_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_BAND3_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_BAND4_DB, 0f)
            .putFloat(PresetPreferenceKeys.NAM_EQ_BAND5_DB, 0f)
            .putBoolean(PresetPreferenceKeys.NAM_EQ_PRE, false)
            .putBoolean(PresetPreferenceKeys.NAM_EQ_ENABLED, true)
            .putBoolean(PresetPreferenceKeys.NAM_NORMALIZE, moduleType != "PEDAL")
            .putBoolean(PresetPreferenceKeys.NAM_A2_FULL, moduleType == "AMP")
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(PresetPreferenceKeys.LAST_MODEL_PATH)
            .remove(PresetPreferenceKeys.LAST_MODEL_NAME)
            .remove(PresetPreferenceKeys.LAST_MODEL_SIZE)
            .remove(PresetPreferenceKeys.LAST_TONE_ID)
            .remove(PresetPreferenceKeys.LAST_TONE_TITLE)
            .apply()
    }
}
