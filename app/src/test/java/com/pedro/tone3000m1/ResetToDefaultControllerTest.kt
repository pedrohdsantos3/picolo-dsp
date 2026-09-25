package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.ResetToDefaultController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetToDefaultControllerTest {
    @Test
    fun clearsAudioTonePreferencesAndBypassInOrder() {
        val events = mutableListOf<String>()
        val controller = controller(events)

        assertTrue(controller.reset())

        assertEquals(
            listOf(
                "clear-nam",
                "clear-ir",
                "clear-fx:0", "clear-fx:1", "clear-fx:2", "clear-fx:3",
                "clear-fx:4", "clear-fx:5", "clear-fx:6", "clear-fx:7",
                "clear-active-tone",
                "clear-extra-nam",
                "eq-low:0.0", "eq-mid:0.0", "eq-high:0.0", "eq-enabled:true",
                "clear-settings",
                "bypass:false",
            ),
            events,
        )
    }

    @Test
    fun returnsFalseAndReportsFailureWithoutContinuingReset() {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("audio engine unavailable")
        val controller = controller(
            events,
            clearImpulseResponse = { throw failure },
            reportError = { events += "error:${it.message}" },
        )

        assertFalse(controller.reset())

        assertEquals(listOf("clear-nam", "error:audio engine unavailable"), events)
    }

    private fun controller(
        events: MutableList<String>,
        clearImpulseResponse: () -> Unit = { events += "clear-ir" },
        reportError: (Exception) -> Unit = { events += "error:${it.message}" },
    ) = ResetToDefaultController(
        clearNamChain = { events += "clear-nam" },
        clearImpulseResponse = clearImpulseResponse,
        clearFxImpulseResponse = { slot -> events += "clear-fx:$slot" },
        clearActiveTone = { events += "clear-active-tone" },
        clearExtraNamChain = { events += "clear-extra-nam" },
        setEqLow = { events += "eq-low:$it" },
        setEqMid = { events += "eq-mid:$it" },
        setEqHigh = { events += "eq-high:$it" },
        setEqEnabled = { events += "eq-enabled:$it" },
        clearPersistedSettings = { events += "clear-settings" },
        setBypass = { events += "bypass:$it" },
        reportError = reportError,
    )
}
