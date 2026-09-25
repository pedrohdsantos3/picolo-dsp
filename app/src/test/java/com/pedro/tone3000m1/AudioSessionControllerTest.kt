package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.AudioSessionController
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSessionControllerTest {
    @Test
    fun startPreparesNativeChainBeforeStartingAndPublishesResult() {
        val events = mutableListOf<String>()
        val controller = controller(
            prepare = { events += "prepare" },
            start = { events += "start"; "AUDIO ACTIVE" },
            publish = { events += "status:$it" },
        )

        val result = controller.startAudio()

        assertEquals("AUDIO ACTIVE", result)
        assertEquals(listOf("prepare", "start", "status:AUDIO ACTIVE"), events)
    }

    @Test
    fun stopStopsNativeAudioAndPublishesStoppedState() {
        val events = mutableListOf<String>()
        val controller = controller(
            stop = { events += "stop" },
            publish = { events += "status:$it" },
        )

        assertEquals("STOPPED", controller.stopAudio())
        assertEquals(listOf("stop", "status:STOPPED"), events)
    }

    private fun controller(
        prepare: () -> Unit = {},
        start: () -> String = { "" },
        stop: () -> Unit = {},
        publish: (String) -> Unit = {},
    ) = AudioSessionController(
        prepareBeforeStart = prepare,
        startNative = start,
        stopNative = stop,
        cycleInputRoute = { 0 },
        cycleOutputRoute = { 0 },
        publishStatus = publish,
    )
}
