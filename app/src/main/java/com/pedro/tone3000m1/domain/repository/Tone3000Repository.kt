package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.OAuthTokenResponse
import com.pedro.tone3000m1.domain.model.OnlineModel
import com.pedro.tone3000m1.domain.model.Tone3000Tone
import java.io.File

internal interface Tone3000Repository {
    fun exchangeAuthorizationCode(code: String, verifier: String): OAuthTokenResponse
    fun getTone(toneId: String, token: String, architecture: Int? = 2): Tone3000Tone
    fun listModels(toneId: String, token: String, architecture: Int? = 2): List<OnlineModel>
    fun downloadModel(model: OnlineModel, token: String, destination: File): File
}
