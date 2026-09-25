package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Synchronous compatibility facade for legacy call sites while DataStore owns persistence.
 * New code should use repositories with suspend APIs instead of this adapter.
 */
internal class DataStoreSharedPreferences(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SharedPreferences {
    private val lock = Any()
    private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private var values: MutableMap<String, Any> = runBlocking(Dispatchers.IO) {
        dataStore.data.first().toValueMap()
    }
    private val updates = Channel<Update>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (update in updates) {
                try {
                    dataStore.edit { preferences -> applyUpdate(preferences, update) }
                    update.completion?.complete(true)
                } catch (_: IOException) {
                    update.completion?.complete(false)
                } catch (_: Exception) {
                    update.completion?.complete(false)
                }
            }
        }
        scope.launch {
            dataStore.data.collect { snapshot -> replaceCache(snapshot.toValueMap()) }
        }
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) { values.deepCopy() }

    override fun getString(key: String, defValue: String?): String? = getValue(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
        val value = values[key] ?: return@synchronized defValues?.toMutableSet()
        (value as? Set<*>)?.filterIsInstance<String>()?.toMutableSet()
            ?: throw ClassCastException("Preference '$key' is ${value::class.java.simpleName}, not a string set.")
    }

    override fun getInt(key: String, defValue: Int): Int = getValue(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = getValue(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = getValue(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = getValue(key, defValue)

    override fun contains(key: String): Boolean = synchronized(lock) { key in values }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    private inline fun <reified T> getValue(key: String, default: T): T = synchronized(lock) {
        val value = values[key] ?: return@synchronized default
        value as? T ?: throw ClassCastException("Preference '$key' is ${value::class.java.simpleName}, not ${T::class.java.simpleName}.")
    }

    private fun replaceCache(snapshot: Map<String, Any>) {
        val changed = synchronized(lock) {
            val keys = (values.keys + snapshot.keys).filter { !values[it].equivalentTo(snapshot[it]) }.toSet()
            values = snapshot.toMutableMap()
            keys to listeners.toList()
        }
        changed.first.forEach { key -> changed.second.forEach { listener -> listener.onSharedPreferenceChanged(this, key) } }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = change(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            change(key, values?.toSet())
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = change(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = change(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = change(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = change(key, value)
        override fun remove(key: String): SharedPreferences.Editor = change(key, null)

        override fun clear(): SharedPreferences.Editor = apply { clearFirst = true }

        override fun commit(): Boolean {
            val completion = CompletableDeferred<Boolean>()
            enqueue(completion)
            return runBlocking { completion.await() }
        }

        override fun apply() {
            enqueue(completion = null)
        }

        private fun change(key: String, value: Any?) = apply {
            changes[key] = value
        }

        private fun enqueue(completion: CompletableDeferred<Boolean>?) {
            val update = Update(clearFirst, changes.toMap(), completion)
            val changedKeys = synchronized(lock) {
                val updated = values.toMutableMap()
                if (clearFirst) updated.clear()
                update.changes.forEach { (key, value) ->
                    if (value == null) updated.remove(key) else updated[key] = value.copyIfMutable()
                }
                val changed = (values.keys + updated.keys).filter { !values[it].equivalentTo(updated[it]) }.toSet()
                values = updated
                changed to listeners.toList()
            }
            if (!updates.trySend(update).isSuccess) completion?.complete(false)
            changedKeys.first.forEach { key -> changedKeys.second.forEach { listener -> listener.onSharedPreferenceChanged(this@DataStoreSharedPreferences, key) } }
        }
    }

    private data class Update(
        val clearFirst: Boolean,
        val changes: Map<String, Any?>,
        val completion: CompletableDeferred<Boolean>?,
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

    private fun Any?.equivalentTo(other: Any?): Boolean = when {
        this is Set<*> && other is Set<*> -> this == other
        else -> this == other
    }
}
