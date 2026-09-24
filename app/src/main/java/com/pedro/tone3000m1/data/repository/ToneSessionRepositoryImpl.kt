package com.pedro.tone3000m1.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import com.pedro.tone3000m1.domain.model.OAuthTokenResponse
import com.pedro.tone3000m1.domain.model.PendingToneAuthorization
import com.pedro.tone3000m1.domain.repository.ToneSessionRepository

internal class ToneSessionRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : ToneSessionRepository {
    override suspend fun saveTokens(tokens: OAuthTokenResponse) {
        preferences.edit {
            it[ACCESS_TOKEN] = tokens.accessToken
            it[REFRESH_TOKEN] = tokens.refreshToken
        }
    }

    override suspend fun saveAccessToken(token: String): Boolean {
        val value = token.trim()
        if (value.isBlank()) {
            clearTokens()
            return false
        }
        preferences.edit { it[ACCESS_TOKEN] = value }
        return true
    }

    override suspend fun accessToken(): String? = preferences.data.first()[ACCESS_TOKEN]

    override suspend fun clearTokens() {
        preferences.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
        }
    }

    override suspend fun savePendingAuthorization(verifier: String, state: String) {
        preferences.edit {
            it[VERIFIER] = verifier
            it[STATE] = state
        }
    }

    override suspend fun pendingAuthorization(): PendingToneAuthorization? {
        val data = preferences.data.first()
        val verifier = data[VERIFIER] ?: return null
        val state = data[STATE] ?: return null
        return PendingToneAuthorization(verifier, state)
    }

    override suspend fun clearPendingAuthorization() {
        preferences.edit {
            it.remove(STATE)
            it.remove(VERIFIER)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val STATE = stringPreferencesKey("oauth_state")
        val VERIFIER = stringPreferencesKey("pkce_verifier")
    }
}
