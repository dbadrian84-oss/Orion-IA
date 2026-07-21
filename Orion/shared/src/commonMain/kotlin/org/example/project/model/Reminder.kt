package org.example.project.model

import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String,
    val date: String, // Stored as yyyy-MM-dd
    val text: String,
    val isCompleted: Boolean = false,
)
