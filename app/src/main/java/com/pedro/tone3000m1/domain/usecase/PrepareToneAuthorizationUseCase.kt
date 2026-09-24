package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.model.ToneAuthorizationChallenge
import com.pedro.tone3000m1.domain.repository.ToneSessionRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Creates and persists PKCE verifier/state before the Activity opens Custom Tabs. */
internal class PrepareToneAuthorizationUseCase(
    private val session: ToneSessionRepository,
) {
    suspend fun execute(): ToneAuthorizationChallenge {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(16)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
        )
        session.savePendingAuthorization(verifier, state)
        return ToneAuthorizationChallenge(state, challenge)
    }

    private fun randomUrlSafe(size: Int): String {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        val secureRandom = SecureRandom()
    }
}
