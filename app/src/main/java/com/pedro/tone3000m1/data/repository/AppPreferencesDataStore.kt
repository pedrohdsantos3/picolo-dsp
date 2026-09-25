package com.pedro.tone3000m1.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Migrates package cache, OAuth session, and module selection; other settings remain in SharedPreferences. */
internal object AppPreferencesDataStore {
    private val Context.dataStore by preferencesDataStore(
        name = DATA_STORE_FILE_NAME,
        produceMigrations = { context ->
            listOf(appPreferencesMigration(context.applicationContext, LEGACY_PREFERENCES_NAME))
        },
    )

    fun get(context: Context): DataStore<Preferences> = context.applicationContext.dataStore

    fun create(
        context: Context,
        legacyPreferencesName: String = LEGACY_PREFERENCES_NAME,
        file: File = context.preferencesDataStoreFile(DATA_STORE_FILE_NAME),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ): DataStore<Preferences> {
        val appContext = context.applicationContext
        return PreferenceDataStoreFactory.create(
            migrations = listOf(appPreferencesMigration(appContext, legacyPreferencesName)),
            scope = scope,
            produceFile = { file },
        )
    }

    private fun appPreferencesMigration(context: Context, preferencesName: String) =
        object : DataMigration<Preferences> {
            private val legacy by lazy { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }

            override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                legacy.all.keys.any(::isMigratedKey)

            override suspend fun migrate(currentData: Preferences): Preferences {
                val migrated = currentData.toMutablePreferences()
                legacy.all.forEach { (key, value) ->
                    if (isMigratedKey(key) && value is String) {
                        val dataStoreKey = stringPreferencesKey(key)
                        if (!migrated.contains(dataStoreKey)) migrated[dataStoreKey] = value
                    }
                }
                return migrated
            }

            override suspend fun cleanUp() {
                val editor = legacy.edit()
                legacy.all.keys.filter(::isMigratedKey).forEach(editor::remove)
                editor.apply()
            }

            private fun isMigratedKey(key: String) =
                key.startsWith(PACKAGE_CAPTURE_KEY_PREFIX) || key in MIGRATED_STRING_KEYS
        }

    private const val LEGACY_PREFERENCES_NAME = "tone3000"
    private const val DATA_STORE_FILE_NAME = "app_preferences"
    const val PACKAGE_CAPTURE_KEY_PREFIX = "package_capture_cache_"
    private val OAUTH_KEYS = setOf("access_token", "refresh_token", "oauth_state", "pkce_verifier")
    private val MIGRATED_STRING_KEYS = OAUTH_KEYS + PresetPreferenceKeys.SELECTED_ADD_TYPE
}
