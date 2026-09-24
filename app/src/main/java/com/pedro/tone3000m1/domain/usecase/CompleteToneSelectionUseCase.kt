package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.ToneSelection
import com.pedro.tone3000m1.domain.repository.Tone3000Repository
import com.pedro.tone3000m1.domain.repository.ToneSessionRepository

/** Exchanges PKCE credentials, persists the session, and loads the chosen tone's captures. */
internal class CompleteToneSelectionUseCase(
    private val tones: Tone3000Repository,
    private val session: ToneSessionRepository,
) {
    suspend fun execute(
        code: String,
        returnedState: String?,
        toneId: String,
        moduleType: String,
        onProgress: (String) -> Unit = {},
    ): ToneSelection {
        val pendingAuthorization = session.pendingAuthorization()
            ?: error("OAuth callback sem sessão PKCE pendente.")
        check(returnedState != null && returnedState == pendingAuthorization.state) {
            "OAuth state mismatch."
        }
        val tokens = tones.exchangeAuthorizationCode(code, pendingAuthorization.verifier)
        session.saveTokens(tokens)
        session.clearPendingAuthorization()

        val normalizedType = moduleType.uppercase()
        val architecture = if (normalizedType == "IR" || normalizedType == "FX") null else 2
        onProgress("2/3 - FETCHING TONE...")
        val tone = tones.getTone(toneId, tokens.accessToken, architecture)
        onProgress(if (architecture == null) "3/3 - FETCHING IMPULSE RESPONSE..." else "3/3 - FETCHING A2 CAPTURES...")
        val models = tones.listModels(toneId, tokens.accessToken, architecture)
        return ToneSelection(tokens, tone, models, normalizedType)
    }
}
