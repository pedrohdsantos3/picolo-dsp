package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.domain.engine.AudioRoutingEngine
import com.pedro.tone3000m1.domain.repository.AudioRoutingRepository

internal class AudioRoutingRepositoryImpl(
    private val preferences: SharedPreferences,
    private val engine: AudioRoutingEngine,
    private val inputChannelKey: String,
    private val outputPairKey: String,
) : AudioRoutingRepository {
    override fun cycleInput(): Int {
        val selected = engine.cycleInputChannel()
        preferences.edit().putInt(inputChannelKey, selected).apply()
        return selected
    }

    override fun cycleOutput(): Int {
        val selected = engine.cycleOutputPair()
        preferences.edit().putInt(outputPairKey, selected).apply()
        return selected
    }

    override fun restoreSavedRoutes() {
        engine.setInputChannel(preferences.getInt(inputChannelKey, 0))
        engine.setOutputPair(preferences.getInt(outputPairKey, 0))
    }

    override fun routingInfo(): String = engine.routingInfo()
}
