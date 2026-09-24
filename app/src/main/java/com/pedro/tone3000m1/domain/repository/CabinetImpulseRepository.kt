package com.pedro.tone3000m1.domain.repository

import java.io.File

internal interface CabinetImpulseRepository {
    fun save(path: File, imageUrl: String, title: String, toneId: String, moduleType: String, position: Int, mix: Float)
}
