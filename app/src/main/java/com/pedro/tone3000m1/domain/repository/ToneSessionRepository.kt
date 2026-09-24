package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.OAuthTokenResponse
import com.pedro.tone3000m1.domain.model.PendingToneAuthorization

internal interface ToneSessionRepository {
    fun saveTokens(tokens: OAuthTokenResponse)
    fun saveAccessToken(token: String): Boolean
    fun accessToken(): String?
    fun clearTokens()
    fun savePendingAuthorization(verifier: String, state: String)
    fun pendingAuthorization(): PendingToneAuthorization?
    fun clearPendingAuthorization()
}
