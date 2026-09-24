package com.pedro.tone3000m1.domain.engine

/** Operations needed to restore a previously active model after a failed switch. */
internal interface ToneModelEngine {
    fun loadModel(path: String): String
}
