package com.pedro.tone3000m1.domain.model

/** Typed representation of a file backed space effect persisted in the legacy JSON chain. */
data class FxImpulseEntry(
    val toneId: String = "",
    val title: String = "Space FX",
    val image: String = "",
    val modelId: Long = 0L,
    val modelName: String = "",
    val path: String,
    val bypass: Boolean = false,
    val mix: Float = 0.5f,
    val position: Int = 0,
)

/** Typed representation of a built in stereo effect persisted in the legacy JSON chain. */
data class FxNativeEntry(
    val effect: Int,
    val bypass: Boolean = false,
    val mix: Float = 0.35f,
    val param1: Float,
    val param2: Float,
    val param3: Float = 12f,
)
