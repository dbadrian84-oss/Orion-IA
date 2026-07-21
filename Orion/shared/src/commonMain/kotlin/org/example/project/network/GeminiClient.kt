package org.example.project.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GeminiClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    // ── Simple single-turn (legacy, kept for compatibility) ──────────────────

    suspend fun generateContent(prompt: String): String {
        return generateWithHistory(systemInstruction = null, history = emptyList(), userMessage = prompt)
    }

    fun generateContentStream(prompt: String): Flow<String> {
        return generateStreamWithHistory(systemInstruction = null, history = emptyList(), userMessage = prompt)
    }

    // ── Multi-turn with history ──────────────────────────────────────────────

    /**
     * Sends [userMessage] to Gemini including a [history] of previous turns
     * (alternating user/model Content objects) and an optional [systemInstruction].
     * Returns the full response text.
     */
    suspend fun generateWithHistory(
        systemInstruction: String?,
        history: List<Content>,
        userMessage: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"

        return try {
            val contents = history + Content(role = "user", parts = listOf(Part(userMessage)))
            val sysContent = systemInstruction?.let { Content(role = "user", parts = listOf(Part(it))) }
            val requestBody = GeminiRequest(contents = contents, systemInstruction = sysContent)

            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val responseText = httpResponse.body<String>()
            val response = json.decodeFromString<GeminiResponse>(responseText)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Vaya, parece que he tenido un problema interno. ¿Podemos intentarlo de nuevo?"
        } catch (e: Exception) {
            e.printStackTrace()
            "Uf, mis servidores están saturados o sin conexión. Dame unos segundos y repítelo, porfa."
        }
    }

    /**
     * Streams [userMessage] to Gemini with conversation [history] context.
     * Emits response chunks as they arrive.
     */
    fun generateStreamWithHistory(
        systemInstruction: String?,
        history: List<Content>,
        userMessage: String
    ): Flow<String> = flow {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:streamGenerateContent?alt=sse&key=$apiKey"

        try {
            val contents = history + Content(role = "user", parts = listOf(Part(userMessage)))
            val sysContent = systemInstruction?.let { Content(role = "user", parts = listOf(Part(it))) }
            val requestBody = GeminiRequest(contents = contents, systemInstruction = sysContent)
            val jsonFormat = Json { ignoreUnknownKeys = true }

            client.preparePost(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.execute { httpResponse ->
                if (!httpResponse.status.isSuccess()) {
                    throw Exception("API Error: ${httpResponse.status.value}")
                }
                val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data.trim() == "[DONE]") break
                        try {
                            val response = jsonFormat.decodeFromString<GeminiResponse>(data)
                            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (text != null) emit(text)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit("Uf, mis servidores están saturados o sin conexión. Dame unos segundos y repítelo, porfa.")
        }
    }
}

