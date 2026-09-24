package com.pedro.tone3000m1.domain.model

/**
 * Persisted signal-chain entry.
 *
 * This is a data-layer model, not a view model: the same immutable value is
 * used by persistence and native-chain reconstruction.
 */
data class ExtraNamEntry(
    val toneId: String,
    val toneTitle: String,
    val modelId: Long,
    val modelName: String,
    val size: String,
    val path: String,
    val bypass: Boolean,
    val gainDb: Float = -15.0f,
    val inGainDb: Float = 0.0f,
    val mix: Float = 1.0f,
    val eqLowDb: Float = 0.0f,
    val eqMidDb: Float = 0.0f,
    val eqHighDb: Float = 0.0f,
    val eqBand3Db: Float = 0.0f,
    val eqBand4Db: Float = 0.0f,
    val eqBand5Db: Float = 0.0f,
    val eqPre: Boolean = false,
    val eqEnabled: Boolean = true,
    val normalize: Boolean = true,
    val a2Full: Boolean = false,
    val imageUrl: String = "",
    val moduleType: String = "AMP"
)
