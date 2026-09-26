package com.pedro.tone3000m1.data.repository

import android.util.Log
import com.pedro.tone3000m1.domain.model.ExtraNamEntry
import com.pedro.tone3000m1.domain.repository.ExtraNamChainRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Persists the non-primary NAM blocks stored in the legacy chain format. */
class NamChainRepository(
    private val preferences: DataStorePreferenceCache,
    private val chainKey: String,
    private val primaryModelPathKey: String,
) : ExtraNamChainRepository {
    override fun readExtraNamChain(): MutableList<ExtraNamEntry> {
        val raw = preferences.getString(chainKey, null) ?: return mutableListOf()
        return try {
            val items = JSONArray(raw)
            val primaryPath = preferences.getString(primaryModelPathKey, null)
            val seenPaths = mutableSetOf<String>()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val path = item.optString("path")
                    if (path.isBlank() || !File(path).exists() || path == primaryPath || !seenPaths.add(path)) {
                        continue
                    }
                    add(item.toEntry(path))
                }
            }.toMutableList()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to parse extra NAM chain", error)
            mutableListOf()
        }
    }

    override fun persistExtraNamChain(entries: List<ExtraNamEntry>) {
        val primaryPath = preferences.getString(primaryModelPathKey, null)
        val seenPaths = mutableSetOf<String>()
        val json = JSONArray()
        entries.asSequence()
            .filter { it.path != primaryPath && seenPaths.add(it.path) }
            .forEach { json.put(it.toJson()) }
        preferences.edit().putString(chainKey, json.toString()).apply()
    }

    private fun JSONObject.toEntry(path: String) = ExtraNamEntry(
        toneId = optString("toneId"),
        toneTitle = optString("toneTitle"),
        modelId = optLong("modelId"),
        modelName = optString("modelName", File(path).name),
        size = optString("size", "unknown"),
        path = path,
        bypass = optBoolean("bypass", false),
        gainDb = optDouble("gainDb", -15.0).toFloat(),
        inGainDb = optDouble("inGainDb", 0.0).toFloat(),
        mix = optDouble("mix", 1.0).toFloat(),
        eqLowDb = optDouble("eqLowDb", 0.0).toFloat(),
        eqMidDb = optDouble("eqMidDb", 0.0).toFloat(),
        eqHighDb = optDouble("eqHighDb", 0.0).toFloat(),
        eqBand3Db = optDouble("eqBand3Db", 0.0).toFloat(),
        eqBand4Db = optDouble("eqBand4Db", 0.0).toFloat(),
        eqBand5Db = optDouble("eqBand5Db", 0.0).toFloat(),
        eqPre = optBoolean("eqPre", false),
        eqEnabled = optBoolean("eqEnabled", true),
        normalize = optBoolean("normalize", true),
        a2Full = optBoolean("a2Full", false),
        imageUrl = optString("imageUrl", ""),
        moduleType = optString("moduleType", "AMP").uppercase(Locale.US),
    )

    private fun ExtraNamEntry.toJson() = JSONObject()
        .put("toneId", toneId)
        .put("toneTitle", toneTitle)
        .put("modelId", modelId)
        .put("modelName", modelName)
        .put("size", size)
        .put("path", path)
        .put("bypass", bypass)
        .put("gainDb", gainDb)
        .put("inGainDb", inGainDb)
        .put("mix", mix)
        .put("eqLowDb", eqLowDb)
        .put("eqMidDb", eqMidDb)
        .put("eqHighDb", eqHighDb)
        .put("eqBand3Db", eqBand3Db)
        .put("eqBand4Db", eqBand4Db)
        .put("eqBand5Db", eqBand5Db)
        .put("eqPre", eqPre)
        .put("eqEnabled", eqEnabled)
        .put("normalize", normalize)
        .put("a2Full", a2Full)
        .put("imageUrl", imageUrl)
        .put("moduleType", moduleType)

    private companion object {
        const val TAG = "NamChainRepository"
    }
}
