package com.pedro.tone3000m1.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Imports supported legacy preference values into DataStore and keeps the legacy file as a recovery copy. */
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

            override suspend fun shouldMigrate(currentData: Preferences): Boolean = legacy.all.any { (name, value) ->
                isSupported(value) && currentData.asMap().keys.none { it.name == name }
            }

            override suspend fun migrate(currentData: Preferences): Preferences {
                val migrated = currentData.toMutablePreferences()
                legacy.all.forEach { (name, value) ->
                    if (migrated.asMap().keys.none { it.name == name }) {
                        migrated.putLegacyValue(name, value)
                    }
                }
                return migrated
            }

            override suspend fun cleanUp() = Unit
        }

    private const val LEGACY_PREFERENCES_NAME = "tone3000"
    private const val DATA_STORE_FILE_NAME = "app_preferences"

    private fun isSupported(value: Any?): Boolean = when (value) {
        is String, is Boolean, is Int, is Long, is Float -> true
        is Set<*> -> value.all { it is String }
        else -> false
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putLegacyValue(name: String, value: Any?) {
        when (value) {
            is String -> this[stringPreferencesKey(name)] = value
            is Boolean -> this[booleanPreferencesKey(name)] = value
            is Int -> this[intPreferencesKey(name)] = value
            is Long -> this[longPreferencesKey(name)] = value
            is Float -> this[floatPreferencesKey(name)] = value
            is Set<*> -> if (value.all { it is String }) {
                this[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
            }
        }
    }
}
