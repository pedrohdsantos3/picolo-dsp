package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Non-blocking snapshot access for the remaining legacy UI callbacks.
 * DataStore remains the only runtime persistence source; new code should use typed repositories.
 */
class DataStorePreferenceCache(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private var values: MutableMap<String, Any> = mutableMapOf()
    private val loaded = CompletableDeferred<Unit>()
    private val updates = Channel<Update>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (update in updates) {
                try {
                    loaded.await()
                    if (update.clearFirst || update.changes.isNotEmpty()) {
                        dataStore.edit { preferences -> applyUpdate(preferences, update) }
                    }
                    update.completion?.complete(Unit)
                } catch (error: IOException) {
                    update.completion?.completeExceptionally(error)
                    android.util.Log.e(TAG, "Could not persist preference update", error)
                } catch (error: Exception) {
                    update.completion?.completeExceptionally(error)
                    android.util.Log.e(TAG, "Could not persist preference update", error)
                }
            }
        }
        scope.launch {
            dataStore.data.collect { snapshot ->
                replaceCache(snapshot.toValueMap())
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() {
        loaded.await()
    }

    /** Waits until all preference updates enqueued before this call have reached DataStore. */
    suspend fun awaitPendingWrites() {
        val completion = CompletableDeferred<Unit>()
        updates.send(Update(clearFirst = false, changes = emptyMap(), completion = completion))
        completion.await()
    }

    fun getAll(): MutableMap<String, *> = synchronized(lock) { values.deepCopy() }

    fun getString(key: String, defValue: String? = null): String? = getValue(key, defValue)

    fun getStringSet(key: String, defValues: MutableSet<String>? = null): MutableSet<String>? = synchronized(lock) {
        val value = values[key] ?: return@synchronized defValues?.toMutableSet()
        (value as? Set<*>)?.filterIsInstance<String>()?.toMutableSet()
            ?: throw ClassCastException("Preference '$key' is ${value::class.java.simpleName}, not a string set.")
    }

    fun getInt(key: String, defValue: Int): Int = getValue(key, defValue)

    fun getLong(key: String, defValue: Long): Long = getValue(key, defValue)

    fun getFloat(key: String, defValue: Float): Float = getValue(key, defValue)

    fun getBoolean(key: String, defValue: Boolean): Boolean = getValue(key, defValue)

    fun contains(key: String): Boolean = synchronized(lock) { key in values }

    fun edit(): Editor = Editor()

    private inline fun <reified T> getValue(key: String, default: T): T = synchronized(lock) {
        val value = values[key] ?: return@synchronized default
        value as? T ?: throw ClassCastException("Preference '$key' is ${value::class.java.simpleName}, not ${T::class.java.simpleName}.")
    }

    private fun replaceCache(snapshot: Map<String, Any>) {
        synchronized(lock) { values = snapshot.toMutableMap() }
    }

    inner class Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clearFirst = false

        fun putString(key: String, value: String?): Editor = change(key, value)
        fun putStringSet(key: String, values: Set<String>?): Editor =
            change(key, values?.toSet())
        fun putInt(key: String, value: Int): Editor = change(key, value)
        fun putLong(key: String, value: Long): Editor = change(key, value)
        fun putFloat(key: String, value: Float): Editor = change(key, value)
        fun putBoolean(key: String, value: Boolean): Editor = change(key, value)
        fun remove(key: String): Editor = change(key, null)

        fun clear(): Editor = apply { clearFirst = true }

        fun apply() = enqueue()

        private fun change(key: String, value: Any?) = apply {
            changes[key] = value
        }

        private fun enqueue() {
            val update = Update(clearFirst, changes.toMap())
            synchronized(lock) {
                val updated = values.toMutableMap()
                if (clearFirst) updated.clear()
                update.changes.forEach { (key, value) ->
                    if (value == null) updated.remove(key) else updated[key] = value.copyIfMutable()
                }
                values = updated
            }
            if (!updates.trySend(update).isSuccess) {
                android.util.Log.e(TAG, "Preference update queue is closed")
            }
        }
    }

    private data class Update(
        val clearFirst: Boolean,
        val changes: Map<String, Any?>,
        val completion: CompletableDeferred<Unit>? = null,
    )

    private fun applyUpdate(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        update: Update,
    ) {
        if (update.clearFirst) preferences.clear()
        update.changes.forEach { (name, value) ->
            preferences.removeName(name)
            if (value != null) preferences.putValue(name, value)
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.removeName(name: String) {
        asMap().keys.filter { it.name == name }.forEach { remove(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun androidx.datastore.preferences.core.MutablePreferences.putValue(name: String, value: Any) {
        when (value) {
            is String -> this[stringPreferencesKey(name)] = value
            is Boolean -> this[booleanPreferencesKey(name)] = value
            is Int -> this[intPreferencesKey(name)] = value
            is Long -> this[longPreferencesKey(name)] = value
            is Float -> this[floatPreferencesKey(name)] = value
            is Set<*> -> this[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
            else -> throw IllegalArgumentException("Unsupported preference value for '$name': ${value::class.java.name}")
        }
    }

    private fun Preferences.toValueMap(): MutableMap<String, Any> = asMap().mapNotNull { (key, value) ->
        when (value) {
            is String, is Boolean, is Int, is Long, is Float -> key.name to value
            is Set<*> -> key.name to value.filterIsInstance<String>().toSet()
            else -> null
        }
    }.toMap().toMutableMap()

    private fun MutableMap<String, Any>.deepCopy(): MutableMap<String, *> = mapValuesTo(mutableMapOf()) { it.value.copyIfMutable() }

    private fun Any.copyIfMutable(): Any = if (this is Set<*>) filterIsInstance<String>().toMutableSet() else this

    private companion object {
        const val TAG = "DataStorePreferenceCache"
    }
}
