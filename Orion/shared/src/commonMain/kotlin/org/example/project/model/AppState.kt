package org.example.project.model

sealed class AppState {
    data object ApiKeyEntry : AppState()
    data class ModelDownload(val apiKey: String) : AppState()
    data class UserDetailsEntry(val apiKey: String) : AppState()
    data class PersonalitySelection(val apiKey: String, val userName: String, val userGender: String) : AppState()
    data class VoiceSelection(val apiKey: String, val userName: String, val userGender: String, val personality: Personality) : AppState()
    data class PermissionsTutorial(val apiKey: String, val userName: String, val userGender: String, val personality: Personality, val voiceId: String) : AppState()
    data class VoiceMode(val apiKey: String, val userName: String, val userGender: String, val personality: Personality, val voiceId: String) : AppState()
    data class Chat(val apiKey: String, val userName: String, val userGender: String, val personality: Personality, val voiceId: String) : AppState()
    data class Configuration(val apiKey: String, val userName: String, val userGender: String, val personality: Personality, val voiceId: String) : AppState()
}
