package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/** Suspending access to the legacy import-mode preference used across OAuth selection. */
internal class PendingImportModeRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun read(): String? = dataStore.data.first()[KEY]

    suspend fun save(mode: String) {
        dataStore.edit { it[KEY] = mode }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("pending_import_mode")
    }
}
