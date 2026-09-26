package com.pedro.tone3000m1.domain.repository

import java.io.File

internal interface CurrentToneRepository {
    suspend fun currentModelFile(): File?
}
