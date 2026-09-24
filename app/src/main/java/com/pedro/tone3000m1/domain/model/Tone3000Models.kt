package com.pedro.tone3000m1.domain.model

import java.io.File

internal data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

internal data class Tone3000Tone(
    val title: String,
)

internal data class ToneSelection(
    val token: OAuthTokenResponse,
    val tone: Tone3000Tone,
    val models: List<OnlineModel>,
    val moduleType: String,
)

internal data class PendingToneAuthorization(
    val verifier: String,
    val state: String,
)

internal data class ToneAuthorizationChallenge(
    val state: String,
    val codeChallenge: String,
)

internal data class ImportedNamFile(
    val originalName: String,
    val file: File,
)
