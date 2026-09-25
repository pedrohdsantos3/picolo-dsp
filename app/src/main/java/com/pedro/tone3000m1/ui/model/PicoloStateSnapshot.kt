package com.pedro.tone3000m1.ui.model

/** Typed state sent to Compose alongside the periodically sampled audio metrics. */
internal data class PicoloStateSnapshot(
    val state: PicoloUiState,
    val stats: String,
)
