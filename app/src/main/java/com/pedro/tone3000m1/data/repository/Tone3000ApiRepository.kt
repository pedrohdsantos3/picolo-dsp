package com.pedro.tone3000m1.data.repository

import android.os.SystemClock
import android.util.Log
import com.pedro.tone3000m1.data.model.OnlineModel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

internal data class Tone3000Tone(
    val title: String,
)

/** Synchronous TONE3000 API calls. Call from an I/O thread. */
internal class Tone3000ApiRepository(
    private val apiBase: String,
    private val clientId: String,
    private val redirectUri: String,
) {
    fun exchangeAuthorizationCode(code: String, verifier: String): OAuthTokenResponse {
        val fields = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to redirectUri,
            "client_id" to clientId,
        )
        val body = fields.entries.joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "POST /oauth/token START")

        val connection = URL("$apiBase/api/v1/oauth/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")
            connection.setFixedLengthStreamingMode(bodyBytes.size)
            connection.outputStream.use { it.write(bodyBytes) }

            val responseCode = connection.responseCode
            Log.i(TAG, "POST /oauth/token HTTP $responseCode in ${SystemClock.elapsedRealtime() - started}ms")
            val response = readResponse(connection, responseCode)
            if (responseCode !in 200..299) {
                throw RuntimeException("Token exchange failed HTTP $responseCode\n$response")
            }

            val json = JSONObject(response)
            return OAuthTokenResponse(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun getTone(toneId: String, token: String, architecture: Int? = 2): Tone3000Tone {
        val url = "$apiBase/api/v1/tones/${urlEncode(toneId)}" +
            (architecture?.let { "?architecture=$it" } ?: "")
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "GET /tones/$toneId START")

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")

            val responseCode = connection.responseCode
            Log.i(TAG, "GET /tones/$toneId HTTP $responseCode in ${SystemClock.elapsedRealtime() - started}ms")
            val response = readResponse(connection, responseCode)
            if (responseCode !in 200..299) {
                throw RuntimeException("Get tone failed HTTP $responseCode\n$response")
            }
            return Tone3000Tone(JSONObject(response).optString("title", "Tone $toneId"))
        } finally {
            connection.disconnect()
        }
    }

    fun listModels(toneId: String, token: String, architecture: Int? = 2): List<OnlineModel> {
        val result = mutableListOf<OnlineModel>()
        var page = 1
        var totalPages = 1

        do {
            val architectureQuery = architecture?.let { "&architecture=$it" } ?: ""
            val url = "$apiBase/api/v1/models?tone_id=${urlEncode(toneId)}$architectureQuery&page=$page&page_size=300"
            val started = SystemClock.elapsedRealtime()
            Log.i(TAG, "GET /models START page=$page url=$url")

            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                Log.i(TAG, "GET /models connecting page=$page...")
                connection.connect()

                val responseCode = connection.responseCode
                Log.i(TAG, "GET /models page=$page HTTP $responseCode in ${SystemClock.elapsedRealtime() - started}ms")
                val response = readResponse(connection, responseCode)
                Log.i(TAG, "GET /models page=$page body received: ${response.length} chars")
                if (responseCode !in 200..299) {
                    throw RuntimeException("List models failed HTTP $responseCode\n$response")
                }

                val root = JSONObject(response)
                val data = root.getJSONArray("data")
                totalPages = root.optInt("total_pages", 1).coerceAtLeast(1)
                Log.i(TAG, "GET /models page=$page parsed: ${data.length()} captures; totalPages=$totalPages")
                for (index in 0 until data.length()) {
                    val item = data.getJSONObject(index)
                    result.add(
                        OnlineModel(
                            id = item.getLong("id"),
                            name = item.optString("name", "capture-${item.optLong("id", index.toLong())}"),
                            size = item.optString("size", "custom"),
                            modelUrl = item.getString("model_url"),
                        ),
                    )
                }
            } finally {
                connection.disconnect()
            }

            page += 1
            if (page > 100) {
                throw RuntimeException("Too many model pages returned by TONE3000.")
            }
        } while (page <= totalPages)

        return result.distinctBy { it.id }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private companion object {
        const val TAG = "Tone3000Api"
    }
}
