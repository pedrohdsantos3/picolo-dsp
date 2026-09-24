package com.pedro.tone3000m1.data.model

/** Serialized state exposed by the current Android and JNI integration. */
internal data class PicoloStateSnapshot(
    val pluginState: String,
    val status: String,
    val stats: String,
)
