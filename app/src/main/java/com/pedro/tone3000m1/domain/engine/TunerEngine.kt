package com.pedro.tone3000m1.domain.engine

/** Low-latency tuner controls and measurements provided by the audio engine. */
internal interface TunerEngine {
    fun setTunerEnabled(enabled: Boolean)
    fun setOutputMuted(muted: Boolean)
    fun tunerFrequencyHz(): Float
    fun tunerInputLevel(): Float
}
