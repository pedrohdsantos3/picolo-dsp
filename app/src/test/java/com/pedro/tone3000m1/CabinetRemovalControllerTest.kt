package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.CabinetRemovalController
import org.junit.Assert.assertEquals
import org.junit.Test

class CabinetRemovalControllerTest {
    @Test
    fun removesNativeAndPersistedStateThenResumesWhenOtherModulesRemain() {
        val events = mutableListOf<String>()
        val controller = CabinetRemovalController(
            isAudioRunning = { true },
            resetNativeImpulseResponse = { events += "reset-native" },
            readImpulseResponsePath = { "/cabinet.wav" },
            deleteFile = { events += "delete:$it" },
            clearPersistedImpulseResponse = { events += "clear-prefs" },
            hasOtherModules = { events += "has-other-modules"; true },
            restartAudio = { events += "restart"; "AUDIO ACTIVE" },
            publishStatus = { events += "status:$it" },
        )

        controller.remove()

        assertEquals(
            listOf(
                "reset-native",
                "delete:/cabinet.wav",
                "clear-prefs",
                "has-other-modules",
                "restart",
                "status:CABINET IR REMOVED\nAUDIO ACTIVE",
            ),
            events,
        )
    }

    @Test
    fun doesNotRestartAudioWhenNoOtherModulesRemain() {
        val events = mutableListOf<String>()
        val controller = CabinetRemovalController(
            isAudioRunning = { true },
            resetNativeImpulseResponse = {},
            readImpulseResponsePath = { null },
            deleteFile = {},
            clearPersistedImpulseResponse = {},
            hasOtherModules = { false },
            restartAudio = { events += "restart"; "AUDIO ACTIVE" },
            publishStatus = events::add,
        )

        controller.remove()

        assertEquals(listOf("CABINET IR REMOVED"), events)
    }
}
