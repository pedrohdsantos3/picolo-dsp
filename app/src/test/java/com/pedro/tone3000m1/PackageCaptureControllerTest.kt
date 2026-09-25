package com.pedro.tone3000m1

import com.pedro.tone3000m1.controller.PackageCaptureController
import com.pedro.tone3000m1.controller.PackageCaptureSource
import com.pedro.tone3000m1.domain.model.OnlineModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageCaptureControllerTest {
    @Test
    fun loadsCapturesAndReturnsPackageContextToUi() {
        val source = source()
        val model = OnlineModel(id = 17L, name = "Capture", size = "small", modelUrl = "model.nam")
        var loaded: Triple<PackageCaptureSource, List<OnlineModel>, String>? = null
        val controller = controller(
            resolve = { source },
            token = { "session-token" },
            load = { _, token ->
                assertEquals("session-token", token)
                listOf(model)
            },
            onLoaded = { item, models, token -> loaded = Triple(item, models, token) },
        )

        assertTrue(controller.selectPackageCaptures("nam-0"))

        assertEquals(Triple(source, listOf(model), "session-token"), loaded)
    }

    @Test
    fun reportsMissingSessionWithoutLoadingCaptures() {
        val unavailable = mutableListOf<String>()
        var didLoad = false
        val controller = controller(
            resolve = { source() },
            token = { null },
            load = { _, _ -> didLoad = true; emptyList() },
            unavailable = unavailable::add,
        )

        assertTrue(controller.selectPackageCaptures("nam-0"))

        assertFalse(didLoad)
        assertEquals(listOf("Sign in to TONE3000 to view this package's captures."), unavailable)
    }

    @Test
    fun returnsFalseForMalformedBlockId() {
        val controller = controller(resolve = { source() })

        assertFalse(controller.selectPackageCaptures("nam-invalid"))
        assertFalse(controller.selectPackageCaptures("fx-invalid"))
    }

    private fun controller(
        resolve: (String) -> PackageCaptureSource?,
        token: suspend () -> String? = { "token" },
        load: suspend (PackageCaptureSource, String) -> List<OnlineModel> = { _, _ -> emptyList() },
        unavailable: (String) -> Unit = {},
        onLoaded: (PackageCaptureSource, List<OnlineModel>, String) -> Unit = { _, _, _ -> },
    ) = PackageCaptureController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        resolveSource = resolve,
        accessToken = token,
        loadCaptures = load,
        publishStatus = {},
        showUnavailable = unavailable,
        onCapturesLoaded = onLoaded,
        reportError = { _, _ -> },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun source() = PackageCaptureSource(
        blockId = "nam-0",
        toneId = "tone-123",
        toneTitle = "Tone package",
        moduleType = "AMP",
        imageUrl = "image.png",
        importMode = "replace:0",
    )
}
