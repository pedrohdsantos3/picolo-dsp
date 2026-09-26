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
    val outputGainDb: Float = 0f,
    val tempoSync: Boolean = false,
    val tapTempoBpm: Float = 120f,
    val leftNote: String = "1/4",
    val rightNote: String = "1/8.",
    /** NAM blocks before this effect; 5 means post-chain stereo for the current 4-slot graph. */
    val position: Int = 5,
)

internal object DualDelayTiming {
    val noteValues = listOf("1/16", "1/8", "1/8.", "1/4", "1/4T", "1/4.", "1/2")

    fun beats(note: String): Float = when (note) {
        "1/16" -> 0.25f
        "1/8" -> 0.5f
        "1/8." -> 0.75f
        "1/4T" -> 2f / 3f
        "1/4." -> 1.5f
        "1/2" -> 2f
        else -> 1f
    }

    fun toMilliseconds(note: String, bpm: Float): Float {
        return (60000f / bpm.coerceIn(40f, 240f) * beats(note)).coerceIn(20f, 2000f)
    }

    fun toHertz(note: String, bpm: Float): Float =
        (bpm.coerceIn(40f, 240f) / (60f * beats(note))).coerceIn(0.1f, 8f)
}
