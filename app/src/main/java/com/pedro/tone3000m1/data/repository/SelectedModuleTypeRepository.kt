package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Stores the user's last selected module type in Preferences DataStore. */
internal class SelectedModuleTypeRepository(
    private val preferences: DataStore<Preferences>,
) {
    suspend fun read(): String {
        val stored = preferences.data.first()[SELECTED_ADD_TYPE]
            ?.uppercase(Locale.US)
            ?.takeIf { it in SUPPORTED_MODULE_TYPES }
        return stored ?: DEFAULT_MODULE_TYPE
    }

    suspend fun save(moduleType: String) {
        val normalized = moduleType.uppercase(Locale.US)
        require(normalized in SUPPORTED_MODULE_TYPES) { "Unsupported module type: $moduleType" }
        preferences.edit { it[SELECTED_ADD_TYPE] = normalized }
    }

    private companion object {
        val SELECTED_ADD_TYPE = stringPreferencesKey(PresetPreferenceKeys.SELECTED_ADD_TYPE)
        const val DEFAULT_MODULE_TYPE = "AMP"
        val SUPPORTED_MODULE_TYPES = setOf("AMP", "PEDAL", "FX", "IR")
    }
}
