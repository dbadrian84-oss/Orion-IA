package org.example.project

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import java.io.File
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import org.example.project.model.ChatMessage
import org.example.project.model.Personality
import org.example.project.audio.SpeechToTextManager
import org.example.project.audio.PiperSynthesizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DesktopSpeechToTextManager : SpeechToTextManager {
    override fun startListening(
        silenceThresholdMs: Long,
        volumeThreshold: Float,
        onPartialResult: (String) -> Unit,
        onResult: (String) -> Unit,
        onAudioLevel: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        onError("STT no implementado en Desktop")
    }
    override fun stopListening() {}
    override fun destroy() {}
}

class DesktopPiperSynthesizer : PiperSynthesizer {
    override fun init(modelPath: String, tokensPath: String, dataPath: String) {}
    override fun speak(text: String, onStart: () -> Unit, onComplete: () -> Unit) {
        onComplete()
    }
    override fun stop() {}
    override fun destroy() {}
}

class DesktopSettingsManager : SettingsManager {
    private val settingsFile = File(System.getProperty("user.home"), ".orion_settings.properties")

    private fun loadProps(): Properties {
        val props = Properties()
        if (settingsFile.exists()) {
            FileInputStream(settingsFile).use { props.load(it) }
        }
        return props
    }

    private fun saveProps(props: Properties) {
        FileOutputStream(settingsFile).use { props.store(it, null) }
    }

    override fun saveApiKey(apiKey: String) {
        val props = loadProps()
        props.setProperty("api_key", apiKey)
        saveProps(props)
    }

    override fun getApiKey(): String? {
        return loadProps().getProperty("api_key")?.takeIf { it.isNotBlank() }
    }

    override fun saveUserDetails(name: String, gender: String) {
        val props = loadProps()
        props.setProperty("user_name", name)
        props.setProperty("user_gender", gender)
        saveProps(props)
    }

    override fun getUserName(): String? {
        return loadProps().getProperty("user_name")?.takeIf { it.isNotBlank() }
    }
    override fun getUserGender(): String? {
        return loadProps().getProperty("user_gender")?.takeIf { it.isNotBlank() }
    }

    override fun saveChatHistory(messages: List<ChatMessage>) {
        val props = loadProps()
        val jsonString = Json.encodeToString(messages)
        props.setProperty("chat_history", jsonString)
        saveProps(props)
    }

    override fun getChatHistory(): List<ChatMessage> {
        val jsonString = loadProps().getProperty("chat_history")
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun setModelDownloaded(downloaded: Boolean) {}
    override fun isModelDownloaded(): Boolean = false
    override fun savePersonality(personality: Personality) {}
    override fun getPersonality(): Personality? = null
    override fun saveVoiceId(voiceId: String) {}
    override fun getVoiceId(): String? = null
    override fun saveImportantFacts(facts: List<String>) {}
    override fun getImportantFacts(): List<String> = emptyList()
    
    override fun saveReminders(reminders: List<org.example.project.model.Reminder>) {}
    override fun getReminders(): List<org.example.project.model.Reminder> = emptyList()
    override fun saveLastDailyGreetingDate(date: String) {}
    override fun getLastDailyGreetingDate(): String? = null
    
    override fun saveEventRules(rules: List<org.example.project.model.EventRule>) {}
    override fun getEventRules(): List<org.example.project.model.EventRule> = emptyList()
    
    override fun setTutorialCompleted(completed: Boolean) {}
    override fun isTutorialCompleted(): Boolean = false
    
    override fun setAlwaysListening(enabled: Boolean) {}
    override fun isAlwaysListening(): Boolean = false
    
    override fun setAssistantSilenceTimeout(seconds: Int) {}
    override fun getAssistantSilenceTimeout(): Int = 10
}

fun main() = application {
    val state = rememberWindowState(size = DpSize(390.dp, 844.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Orion",
        state = state,
        resizable = false          // mantiene el aspecto de móvil
    ) {
        val settingsManager = DesktopSettingsManager()
        val speechToTextManager = DesktopSpeechToTextManager()
        val piperSynthesizer = DesktopPiperSynthesizer()
        
        App(
            settingsManager = settingsManager,
            speechToTextManager = speechToTextManager,
            piperSynthesizer = piperSynthesizer
        )
    }
}
