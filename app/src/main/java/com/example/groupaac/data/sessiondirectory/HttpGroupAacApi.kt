package com.example.groupaac.data.sessiondirectory

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class HttpGroupAacApi(
    private val baseUrl: String,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : GroupAacApi {
    override suspend fun createSession(
        request: CreateSessionApiRequest
    ): SessionApiResponse = request(
        method = "POST",
        path = "/sessions",
        requestBody = json.encodeToString(CreateSessionApiRequest.serializer(), request)
    )

    override suspend fun resolveJoinCode(
        request: ResolveCodeApiRequest
    ): SessionApiResponse = request(
        method = "POST",
        path = "/sessions/resolve-code",
        requestBody = json.encodeToString(ResolveCodeApiRequest.serializer(), request)
    )

    override suspend fun updateSession(
        sessionId: String,
        request: UpdateSessionApiRequest
    ): SessionApiResponse = request(
        method = "PATCH",
        path = "/sessions/$sessionId",
        requestBody = json.encodeToString(UpdateSessionApiRequest.serializer(), request)
    )

    override suspend fun endSession(
        sessionId: String,
        request: CloseSessionApiRequest
    ): SessionApiResponse = request(
        method = "POST",
        path = "/sessions/$sessionId/end",
        requestBody = json.encodeToString(CloseSessionApiRequest.serializer(), request)
    )

    override suspend fun cancelSession(
        sessionId: String,
        request: CloseSessionApiRequest
    ): SessionApiResponse = request(
        method = "POST",
        path = "/sessions/$sessionId/cancel",
        requestBody = json.encodeToString(CloseSessionApiRequest.serializer(), request)
    )

    private suspend fun request(
        method: String,
        path: String,
        requestBody: String
    ): SessionApiResponse = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(requestBody)
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader().use(BufferedReader::readText)

            if (body.isBlank()) {
                SessionApiResponse(
                    result = "FAILURE",
                    message = "Empty response from session directory."
                )
            } else {
                json.decodeFromString<SessionApiResponse>(body)
            }
        } catch (error: Throwable) {
            SessionApiResponse(
                result = "FAILURE",
                message = error.message ?: "Unable to reach session directory."
            )
        } finally {
            connection.disconnect()
        }
    }
}
