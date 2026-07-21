package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.example.project.model.AppState
import org.example.project.ui.screens.*
import org.example.project.ui.theme.OrionTheme

import org.example.project.audio.SpeechToTextManager
import org.example.project.audio.PiperSynthesizer
import org.example.project.audio.ModelDownloader
import org.example.project.network.GeminiClient

@Composable
fun App(
    settingsManager: SettingsManager,
    speechToTextManager: SpeechToTextManager,
    piperSynthesizer: PiperSynthesizer,
    modelDownloader: ModelDownloader,
    isAssistantLaunch: Boolean = false
) {
    OrionTheme {
        var appState by remember { 
            val savedApiKey = settingsManager.getApiKey()
            val savedUserName = settingsManager.getUserName()
            val savedUserGender = settingsManager.getUserGender()
            val modelDownloaded = settingsManager.isModelDownloaded()
            val savedPersonality = settingsManager.getPersonality()
            val savedVoiceId = settingsManager.getVoiceId()
            
            val tutorialCompleted = settingsManager.isTutorialCompleted()
            
            mutableStateOf<AppState>(
                when {
                    savedApiKey != null && savedUserName != null && savedUserGender != null && savedPersonality != null && savedVoiceId != null && tutorialCompleted -> 
                        AppState.VoiceMode(savedApiKey, savedUserName, savedUserGender, savedPersonality, savedVoiceId)
                    savedApiKey != null && savedUserName != null && savedUserGender != null && savedPersonality != null && savedVoiceId != null -> 
                        AppState.PermissionsTutorial(savedApiKey, savedUserName, savedUserGender, savedPersonality, savedVoiceId)
                    savedApiKey != null && savedUserName != null && savedUserGender != null && savedPersonality != null -> 
                        AppState.VoiceSelection(savedApiKey, savedUserName, savedUserGender, savedPersonality)
                    savedApiKey != null && savedUserName != null && savedUserGender != null -> 
                        AppState.PersonalitySelection(savedApiKey, savedUserName, savedUserGender)
                    savedApiKey != null && modelDownloaded -> 
                        AppState.UserDetailsEntry(savedApiKey)
                    savedApiKey != null -> 
                        AppState.ModelDownload(savedApiKey)
                    else -> 
                        AppState.ApiKeyEntry
                }
            ) 
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val currentState = appState) {
                is AppState.ApiKeyEntry -> {
                    TutorialApiKeyScreen(
                        onApiKeySubmitted = { apiKey ->
                            settingsManager.saveApiKey(apiKey)
                            appState = AppState.ModelDownload(apiKey)
                        }
                    )
                }
                is AppState.ModelDownload -> {
                    TutorialModelDownloadScreen(
                        modelDownloader = modelDownloader,
                        onDownloadComplete = {
                            settingsManager.setModelDownloaded(true)
                            appState = AppState.UserDetailsEntry(currentState.apiKey)
                        }
                    )
                }
                is AppState.UserDetailsEntry -> {
                    TutorialUserDetailsScreen(
                        onDetailsSubmitted = { name, gender ->
                            settingsManager.saveUserDetails(name, gender)
                            appState = AppState.PersonalitySelection(currentState.apiKey, name, gender)
                        }
                    )
                }
                is AppState.PersonalitySelection -> {
                    TutorialPersonalityScreen(
                        onPersonalitySelected = { personality ->
                            settingsManager.savePersonality(personality)
                            appState = AppState.VoiceSelection(currentState.apiKey, currentState.userName, currentState.userGender, personality)
                        }
                    )
                }
                is AppState.VoiceSelection -> {
                    TutorialVoiceSelectionScreen(
                        onVoiceSelected = { voiceId ->
                            settingsManager.saveVoiceId(voiceId)
                            appState = AppState.PermissionsTutorial(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, voiceId)
                        }
                    )
                }
                is AppState.PermissionsTutorial -> {
                    TutorialPermissionsScreen(
                        onComplete = {
                            settingsManager.setTutorialCompleted(true)
                            appState = AppState.VoiceMode(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, currentState.voiceId)
                        }
                    )
                }
                is AppState.VoiceMode -> {
                    VoiceScreen(
                        apiKey = currentState.apiKey,
                        userName = currentState.userName,
                        userGender = currentState.userGender,
                        personality = currentState.personality,
                        voiceId = currentState.voiceId,
                        settingsManager = settingsManager,
                        speechToTextManager = speechToTextManager,
                        piperSynthesizer = piperSynthesizer,
                        modelDownloader = modelDownloader,
                        geminiClient = remember { GeminiClient(currentState.apiKey) },
                        isAssistantLaunch = isAssistantLaunch,
                        onNavigateToChat = {
                            appState = AppState.Chat(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, currentState.voiceId)
                        },
                        onNavigateToConfig = {
                            appState = AppState.Configuration(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, currentState.voiceId)
                        }
                    )
                }
                is AppState.Chat -> {
                    ChatScreen(
                        settingsManager = settingsManager,
                        apiKey = currentState.apiKey,
                        userName = currentState.userName,
                        userGender = currentState.userGender,
                        personality = currentState.personality,
                        voiceId = currentState.voiceId,
                        onNavigateToConfig = {
                            appState = AppState.Configuration(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, currentState.voiceId)
                        },
                        onNavigateToVoice = {
                            appState = AppState.VoiceMode(currentState.apiKey, currentState.userName, currentState.userGender, currentState.personality, currentState.voiceId)
                        },
                        onSendMessage = { message ->
                            println("Sending message to AI with personality ${currentState.personality.name}: $message")
                        }
                    )
                }
                is AppState.Configuration -> {
                    ConfigurationScreen(
                        settingsManager = settingsManager,
                        modelDownloader = modelDownloader,
                        userName = currentState.userName,
                        onBack = { newName ->
                            val finalName = newName ?: currentState.userName
                            val finalVoiceId = settingsManager.getVoiceId() ?: currentState.voiceId
                            appState = AppState.VoiceMode(currentState.apiKey, finalName, currentState.userGender, currentState.personality, finalVoiceId)
                        }
                    )
                }
            }
        }
    }
}