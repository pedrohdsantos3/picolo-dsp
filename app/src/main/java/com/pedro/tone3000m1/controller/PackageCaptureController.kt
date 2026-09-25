package com.pedro.tone3000m1.controller

import com.pedro.tone3000m1.domain.model.OnlineModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PackageCaptureSource(
    val blockId: String,
    val toneId: String,
    val toneTitle: String,
    val moduleType: String,
    val imageUrl: String,
    val importMode: String,
)

/** Loads captures for an existing NAM, FX, or cabinet package without blocking the UI thread. */
internal class PackageCaptureController(
    private val scope: CoroutineScope,
    private val resolveSource: (String) -> PackageCaptureSource?,
    private val accessToken: suspend () -> String?,
    private val loadCaptures: suspend (PackageCaptureSource, String) -> List<OnlineModel>,
    private val publishStatus: (String) -> Unit,
    private val showUnavailable: (String) -> Unit,
    private val onCapturesLoaded: (PackageCaptureSource, List<OnlineModel>, String) -> Unit,
    private val reportError: (PackageCaptureSource, Exception) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Returns false only when the block id is malformed; accepted requests report async failures in the UI. */
    fun selectPackageCaptures(blockId: String): Boolean {
        if (
            blockId.startsWith("nam-") && blockId.removePrefix("nam-").toIntOrNull() == null ||
            blockId.startsWith("fx-") && blockId.removePrefix("fx-").toIntOrNull() == null
        ) {
            return false
        }
        val source = resolveSource(blockId)
        if (source == null || source.toneId.isBlank() || source.toneId.startsWith("local-")) {
            showUnavailable(DEFAULT_UNAVAILABLE_MESSAGE)
            return true
        }

        publishStatus("Loading captures from:\n${source.toneTitle}")
        scope.launch {
            try {
                val token = withContext(ioDispatcher) { accessToken() }
                if (token.isNullOrBlank()) {
                    showUnavailable(SIGN_IN_MESSAGE)
                    return@launch
                }

                val models = withContext(ioDispatcher) { loadCaptures(source, token) }
                if (models.isEmpty()) {
                    showUnavailable("No captures found in ${source.toneTitle}.")
                    return@launch
                }
                onCapturesLoaded(source, models, token)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportError(source, error)
                showUnavailable(LOAD_ERROR_MESSAGE)
            }
        }
        return true
    }

    private companion object {
        const val DEFAULT_UNAVAILABLE_MESSAGE = "No package captures are associated with this block."
        const val SIGN_IN_MESSAGE = "Sign in to TONE3000 to view this package's captures."
        const val LOAD_ERROR_MESSAGE = "Couldn't load this package's captures. Check your connection and sign-in."
    }
}
