package com.pedro.tone3000m1.data.repository

import android.content.SharedPreferences
import com.pedro.tone3000m1.domain.model.OAuthTokenResponse
import com.pedro.tone3000m1.domain.model.PendingToneAuthorization
import com.pedro.tone3000m1.domain.repository.ToneSessionRepository

internal class ToneSessionRepositoryImpl(
    private val preferences: SharedPreferences,
) : ToneSessionRepository {
    override fun saveTokens(tokens: OAuthTokenResponse) {
        preferences.edit()
            .putString(ACCESS_TOKEN_KEY, tokens.accessToken)
            .putString(REFRESH_TOKEN_KEY, tokens.refreshToken)
            .apply()
    }

    override fun saveAccessToken(token: String): Boolean {
        val value = token.trim()
        if (value.isBlank()) {
            clearTokens()
            return false
        }
        preferences.edit().putString(ACCESS_TOKEN_KEY, value).apply()
        return true
    }

    override fun accessToken(): String? = preferences.getString(ACCESS_TOKEN_KEY, null)

    override fun clearTokens() {
        preferences.edit()
            .remove(ACCESS_TOKEN_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .apply()
    }

    override fun savePendingAuthorization(verifier: String, state: String) {
        preferences.edit()
            .putString(VERIFIER_KEY, verifier)
            .putString(STATE_KEY, state)
            .apply()
    }

    override fun pendingAuthorization(): PendingToneAuthorization? {
        val verifier = preferences.getString(VERIFIER_KEY, null) ?: return null
        val state = preferences.getString(STATE_KEY, null) ?: return null
        return PendingToneAuthorization(verifier, state)
    }

    override fun clearPendingAuthorization() {
        preferences.edit()
            .remove(STATE_KEY)
            .remove(VERIFIER_KEY)
            .apply()
    }

    private companion object {
        const val ACCESS_TOKEN_KEY = "access_token"
        const val REFRESH_TOKEN_KEY = "refresh_token"
        const val STATE_KEY = "oauth_state"
        const val VERIFIER_KEY = "pkce_verifier"
    }
}
