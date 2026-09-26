package com.pedro.tone3000m1

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pedro.tone3000m1.data.repository.PendingImportModeRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingImportModeRepositoryTest {
    @Test
    fun readsAndClearsTheLegacyPreferenceKey() = runBlocking {
        val storeFile = File.createTempFile("pending-import", ".preferences_pb").also { it.delete() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile })
            val key = stringPreferencesKey("pending_import_mode")
            val repository = PendingImportModeRepository(dataStore)

            dataStore.edit { it[key] = "replace-fx:2" }
            assertEquals("replace-fx:2", repository.read())

            repository.clear()
            assertNull(repository.read())
        } finally {
            storeFile.delete()
            scope.cancel()
        }
    }
}
