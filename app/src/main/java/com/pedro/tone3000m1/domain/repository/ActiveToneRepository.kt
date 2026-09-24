package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.OnlineModel
import java.io.File

internal interface ActiveToneRepository {
    fun save(toneId: String, toneTitle: String, model: OnlineModel, moduleType: String, file: File)
    fun clear()
}
