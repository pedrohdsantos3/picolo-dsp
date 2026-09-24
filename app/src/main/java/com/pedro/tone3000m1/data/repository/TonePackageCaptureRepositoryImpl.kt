package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.repository.TonePackageCaptureRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Persists package capture lists using the app's existing SharedPreferences JSON format. */
internal class TonePackageCaptureRepositoryImpl(
    private val preferences: SharedPreferences,
) : TonePackageCaptureRepository {
    override fun save(toneId: String, moduleType: String, models: List<OnlineModel>) {
        if (toneId.isBlank() || models.isEmpty()) return
        val json = JSONArray()
        models.distinctBy { it.id }.forEach { model ->
            json.put(
                JSONObject()
                    .put("id", model.id)
                    .put("name", model.name)
                    .put("size", model.size)
                    .put("modelUrl", model.modelUrl),
            )
        }
        preferences.edit().putString(key(toneId, moduleType), json.toString()).apply()
    }

    override fun read(toneId: String, moduleType: String): List<OnlineModel> {
        val json = preferences.getString(key(toneId, moduleType), null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id", 0L)
                val modelUrl = item.optString("modelUrl").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                OnlineModel(
                    id = id,
                    name = item.optString("name", "capture-$id"),
                    size = item.optString("size", "custom"),
                    modelUrl = modelUrl,
                )
            }.distinctBy { it.id }
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring invalid package capture cache for tone $toneId", error)
            emptyList()
        }
    }

    private fun key(toneId: String, moduleType: String): String {
        val kind = when (moduleType.uppercase(Locale.US)) {
            "FX" -> "FX"
            "IR" -> "IR"
            else -> "NAM"
        }
        return "package_capture_cache_${kind}_$toneId"
    }

    private companion object {
        const val TAG = "PackageCaptureCache"
    }
}
