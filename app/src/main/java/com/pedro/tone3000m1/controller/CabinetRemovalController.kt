package com.pedro.tone3000m1.controller

/** Clears the cabinet IR from the native graph and persisted module state. */
internal class CabinetRemovalController(
    private val isAudioRunning: () -> Boolean,
    private val resetNativeImpulseResponse: () -> Unit,
    private val readImpulseResponsePath: () -> String?,
    private val deleteFile: (String) -> Unit,
    private val clearPersistedImpulseResponse: () -> Unit,
    private val hasOtherModules: () -> Boolean,
    private val restartAudio: () -> String,
    private val publishStatus: (String) -> Unit,
) {
    fun remove() {
        val resumeAudio = isAudioRunning()
        resetNativeImpulseResponse()
        readImpulseResponsePath()?.let { path ->
            try {
                deleteFile(path)
            } catch (_: Exception) {
                // A stale file must not prevent removing the module from the graph.
            }
        }
        clearPersistedImpulseResponse()

        val shouldResume = resumeAudio && hasOtherModules()
        val audioResult = if (shouldResume) restartAudio() else ""
        publishStatus(
            if (shouldResume) "CABINET IR REMOVED\n$audioResult" else "CABINET IR REMOVED",
        )
    }
}
