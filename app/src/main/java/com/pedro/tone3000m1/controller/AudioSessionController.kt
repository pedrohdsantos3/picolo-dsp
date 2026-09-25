package com.pedro.tone3000m1.controller

/** Coordinates audio start/stop and input/output routing actions. */
internal class AudioSessionController(
    private val prepareBeforeStart: () -> Unit,
    private val startNative: () -> String,
    private val stopNative: () -> Unit,
    private val cycleInputRoute: () -> Int,
    private val cycleOutputRoute: () -> Int,
    private val publishStatus: (String) -> Unit,
) {
    fun startAudio(): String {
        prepareBeforeStart()
        return startNative().also(publishStatus)
    }

    fun stopAudio(): String {
        stopNative()
        return STOPPED_STATUS.also(publishStatus)
    }

    fun cycleInput(): Int = cycleInputRoute()

    fun cycleOutput(): Int = cycleOutputRoute()

    private companion object {
        const val STOPPED_STATUS = "STOPPED"
    }
}
