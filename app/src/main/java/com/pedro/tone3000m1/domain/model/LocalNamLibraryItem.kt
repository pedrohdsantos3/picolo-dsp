package com.pedro.tone3000m1.domain.model

import java.io.File

internal data class LocalNamLibraryItem(
    val file: File,
    val modelName: String,
    val modelSize: String,
    val toneTitle: String,
    val toneId: String,
    val imageUrl: String,
    val moduleType: String,
    val modelId: Long = 0L,
)
