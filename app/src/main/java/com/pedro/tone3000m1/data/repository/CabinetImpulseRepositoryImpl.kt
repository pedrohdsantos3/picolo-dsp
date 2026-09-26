package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pedro.tone3000m1.domain.repository.CabinetImpulseRepository
import java.io.File

/** Writes the existing cabinet IR preference keys and defaults. */
internal class CabinetImpulseRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : CabinetImpulseRepository {
    override suspend fun save(path: File, imageUrl: String, title: String, toneId: String, moduleType: String, position: Int, mix: Float) {
        preferences.edit { values ->
            values[stringPreferencesKey("cabinet_ir_path")] = path.absolutePath
            values[stringPreferencesKey("cabinet_ir_image")] = imageUrl
            values[stringPreferencesKey("cabinet_ir_title")] = title
            values[stringPreferencesKey("cabinet_ir_tone_id")] = toneId
            values[stringPreferencesKey("cabinet_ir_module_type")] = moduleType
            values[intPreferencesKey("cabinet_ir_position")] = position
            values[booleanPreferencesKey("cabinet_ir_bypass")] = false
            values[floatPreferencesKey("cabinet_ir_in_gain_db")] = 0.0f
            values[floatPreferencesKey("cabinet_ir_out_gain_db")] = 0.0f
            values[floatPreferencesKey("cabinet_ir_mix")] = mix
            values[booleanPreferencesKey("cabinet_ir_eq_pre")] = false
            values[booleanPreferencesKey("cabinet_ir_eq_enabled")] = true
            for (band in 0 until 6) {
                values[floatPreferencesKey("cabinet_ir_eq_$band")] = 0.0f
            }
        }
    }
}
