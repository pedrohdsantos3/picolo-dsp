package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.OnlineModel

/** Stores TONE3000 package captures so the in-editor picker can reuse them. */
internal interface TonePackageCaptureRepository {
    suspend fun save(toneId: String, moduleType: String, models: List<OnlineModel>)
    suspend fun read(toneId: String, moduleType: String): List<OnlineModel>
}
