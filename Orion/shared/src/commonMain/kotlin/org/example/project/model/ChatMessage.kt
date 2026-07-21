package org.example.project.model

import kotlinx.serialization.Serializable
import org.example.project.network.Content
import org.example.project.network.Part

@Serializable
data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long
)

/** Converts a stored ChatMessage to Gemini API Content for multi-turn history. */
fun ChatMessage.toGeminiContent(): Content = Content(
    role = if (isUser) "user" else "model",
    parts = listOf(Part(text))
)
