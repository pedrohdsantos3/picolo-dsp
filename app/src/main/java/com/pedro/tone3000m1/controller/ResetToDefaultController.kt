package com.pedro.tone3000m1.controller

/** Restores the audio graph, tone selection and persisted module settings to defaults. */
internal class ResetToDefaultController(
    private val clearNamChain: () -> Unit,
    private val clearImpulseResponse: () -> Unit,
    private val clearFxImpulseResponse: (Int) -> Unit,
    private val clearActiveTone: () -> Unit,
    private val clearExtraNamChain: () -> Unit,
    private val setEqLow: (Float) -> Unit,
    private val setEqMid: (Float) -> Unit,
    private val setEqHigh: (Float) -> Unit,
    private val setEqEnabled: (Boolean) -> Unit,
    private val clearPersistedSettings: () -> Unit,
    private val setBypass: (Boolean) -> Unit,
    private val reportError: (Exception) -> Unit,
) {
    fun reset(): Boolean = try {
        clearNamChain()
        clearImpulseResponse()
        for (slot in 0 until FX_SLOT_COUNT) clearFxImpulseResponse(slot)
        clearActiveTone()
        clearExtraNamChain()
        setEqLow(0.0f)
        setEqMid(0.0f)
        setEqHigh(0.0f)
        setEqEnabled(true)
        clearPersistedSettings()
        setBypass(false)
        true
    } catch (error: Exception) {
        reportError(error)
        false
    }

    private companion object {
        const val FX_SLOT_COUNT = 8
    }
}
