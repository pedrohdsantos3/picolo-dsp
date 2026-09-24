package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the legacy JSON schemas for file based and native FX chains. */
internal class FxChainRepository(
    private val preferences: SharedPreferences,
    private val impulseChainKey: String,
    private val nativeChainKey: String,
) {
    fun readImpulseChain(): MutableList<JSONObject> {
        val array = readArray(impulseChainKey)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank() && File(path).exists()) add(item)
            }
        }.toMutableList()
    }

    fun persistImpulseChain(entries: List<JSONObject>) = persist(impulseChainKey, entries)

    fun readNativeChain(): MutableList<JSONObject> {
        val array = readArray(nativeChainKey)
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }.toMutableList()
    }

    fun persistNativeChain(entries: List<JSONObject>) = persist(nativeChainKey, entries)

    private fun readArray(key: String): JSONArray {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun persist(key: String, entries: List<JSONObject>) {
        val array = JSONArray().also { output -> entries.forEach { entry -> output.put(entry) } }
        preferences.edit().putString(key, array.toString()).apply()
    }
}
