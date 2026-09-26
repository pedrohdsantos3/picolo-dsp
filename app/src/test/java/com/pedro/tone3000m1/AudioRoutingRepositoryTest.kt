package com.pedro.tone3000m1

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import com.pedro.tone3000m1.data.repository.AudioRoutingRepositoryImpl
import com.pedro.tone3000m1.domain.engine.AudioRoutingEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRoutingRepositoryTest {
    @Test
    fun cyclesAndRestoresRoutesThroughDataStore() = runBlocking {
        val storeFile = File.createTempFile("audio-routing", ".preferences_pb").also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })
            val engine = FakeRoutingEngine()
            val repository = AudioRoutingRepositoryImpl(dataStore, engine, "input_channel", "output_pair")

            assertEquals(1, repository.cycleInput())
            assertEquals(1, repository.cycleOutput())
            val values = dataStore.data.first()
            assertEquals(1, values[intPreferencesKey("input_channel")])
            assertEquals(1, values[intPreferencesKey("output_pair")])

            val restoredEngine = FakeRoutingEngine()
            AudioRoutingRepositoryImpl(dataStore, restoredEngine, "input_channel", "output_pair").restoreSavedRoutes()
            assertEquals(1, restoredEngine.input)
            assertEquals(1, restoredEngine.output)
        } finally {
            scope.cancel()
            storeFile.delete()
        }
    }

    private class FakeRoutingEngine : AudioRoutingEngine {
        var input = 0
        var output = 0
        override fun cycleInputChannel() = (++input)
        override fun cycleOutputPair() = (++output)
        override fun setInputChannel(channel: Int) { input = channel }
        override fun setOutputPair(pairIndex: Int) { output = pairIndex }
        override fun routingInfo() = "input=$input output=$output"
    }
}
