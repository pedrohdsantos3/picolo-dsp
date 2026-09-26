package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.pedro.tone3000m1.domain.engine.AudioRoutingEngine
import com.pedro.tone3000m1.domain.repository.AudioRoutingRepository
import kotlinx.coroutines.flow.first

internal class AudioRoutingRepositoryImpl(
    private val preferences: DataStore<Preferences>,
    private val engine: AudioRoutingEngine,
    private val inputChannelKey: String,
    private val outputPairKey: String,
) : AudioRoutingRepository {
    override suspend fun cycleInput(): Int {
        val selected = engine.cycleInputChannel()
        preferences.edit { it[intPreferencesKey(inputChannelKey)] = selected }
        return selected
    }

    override suspend fun cycleOutput(): Int {
        val selected = engine.cycleOutputPair()
        preferences.edit { it[intPreferencesKey(outputPairKey)] = selected }
        return selected
    }

    override suspend fun restoreSavedRoutes() {
        val values = preferences.data.first()
        engine.setInputChannel(values[intPreferencesKey(inputChannelKey)] ?: 0)
        engine.setOutputPair(values[intPreferencesKey(outputPairKey)] ?: 0)
    }

    override fun routingInfo(): String = engine.routingInfo()
}
