package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.OAuthTokenResponse
import com.pedro.tone3000m1.domain.model.PendingToneAuthorization

internal interface ToneSessionRepository {
    suspend fun saveTokens(tokens: OAuthTokenResponse)
    suspend fun saveAccessToken(token: String): Boolean
    suspend fun accessToken(): String?
    suspend fun clearTokens()
    suspend fun savePendingAuthorization(verifier: String, state: String)
    suspend fun pendingAuthorization(): PendingToneAuthorization?
    suspend fun clearPendingAuthorization()
}
