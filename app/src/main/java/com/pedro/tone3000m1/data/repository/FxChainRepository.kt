package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.domain.model.FxImpulseEntry
import com.pedro.tone3000m1.domain.model.FxNativeEntry
import com.pedro.tone3000m1.domain.repository.FxEffectsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the legacy JSON schemas while exposing immutable Kotlin models to callers. */
internal class FxChainRepository(
    private val preferences: SharedPreferences,
    private val impulseChainKey: String,
    private val nativeChainKey: String,
) : FxEffectsRepository {
    override fun readImpulseChain(): MutableList<FxImpulseEntry> {
        val array = readArray(impulseChainKey)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank() && File(path).exists()) {
                    add(
                        FxImpulseEntry(
                            toneId = item.optString("toneId"),
                            title = item.optString("title", "Space FX"),
                            image = item.optString("image", ""),
                            modelId = item.optLong("modelId", 0L),
                            modelName = item.optString("modelName", ""),
                            path = path,
                            bypass = item.optBoolean("bypass", false),
                            mix = item.optDouble("mix", 0.5).toFloat(),
                            position = item.optInt("position", 0),
                        ),
                    )
                }
            }
        }.toMutableList()
    }

    override fun persistImpulseChain(entries: List<FxImpulseEntry>) = persist(
        impulseChainKey,
        entries.map { entry ->
            JSONObject()
                .put("toneId", entry.toneId)
                .put("title", entry.title)
                .put("image", entry.image)
                .put("modelId", entry.modelId)
                .put("modelName", entry.modelName)
                .put("path", entry.path)
                .put("bypass", entry.bypass)
                .put("mix", entry.mix.toDouble())
                .put("position", entry.position)
        },
    )

    fun readNativeChain(): MutableList<FxNativeEntry> {
        val array = readArray(nativeChainKey)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val effect = item.optInt("effect", 0).coerceIn(0, 3)
                add(
                    FxNativeEntry(
                        effect = effect,
                        bypass = item.optBoolean("bypass", false),
                        mix = item.optDouble("mix", 0.35).toFloat(),
                        param1 = item.optDouble("param1", defaultParam1(effect)).toFloat(),
                        param2 = item.optDouble("param2", defaultParam2(effect)).toFloat(),
                        param3 = item.optDouble("param3", 12.0).toFloat(),
                    ),
                )
            }
        }.toMutableList()
    }

    fun persistNativeChain(entries: List<FxNativeEntry>) = persist(
        nativeChainKey,
        entries.map { entry ->
            JSONObject()
                .put("effect", entry.effect)
                .put("bypass", entry.bypass)
                .put("mix", entry.mix.toDouble())
                .put("param1", entry.param1.toDouble())
                .put("param2", entry.param2.toDouble())
                .put("param3", entry.param3.toDouble())
        },
    )

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

    private companion object {
        fun defaultParam1(effect: Int) = when (effect) { 0, 1 -> 350.0; 2 -> 1500.0; else -> 150.0 }
        fun defaultParam2(effect: Int) = when (effect) { 0, 1 -> 0.35; 2 -> 0.5; else -> 5000.0 }
    }
}
