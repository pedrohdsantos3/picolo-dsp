package com.pedro.tone3000m1.domain.engine

/** Native operations needed to validate and activate the primary TONE3000 NAM capture. */
internal interface PrimaryToneCaptureEngine {
    fun loadModel(path: String): String
    fun applyModuleDefaults(moduleType: String)
    fun start(): String
}
