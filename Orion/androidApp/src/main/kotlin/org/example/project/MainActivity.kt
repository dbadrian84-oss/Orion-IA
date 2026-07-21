package org.example.project

import android.content.Context
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import org.example.project.model.ChatMessage
import org.example.project.model.Personality
import org.example.project.audio.AndroidPiperSynthesizer
import org.example.project.audio.AndroidSpeechToTextManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.mutableStateOf
import android.content.Intent

class AndroidSettingsManager(context: Context) : SettingsManager {
    private val prefs = context.getSharedPreferences("orion_settings", Context.MODE_PRIVATE)
    
    override fun saveApiKey(apiKey: String) {
        prefs.edit().putString("api_key", apiKey).apply()
    }
    
    override fun getApiKey(): String? {
        return prefs.getString("api_key", null)
    }

    override fun saveUserDetails(name: String, gender: String) {
        prefs.edit().putString("user_name", name).putString("user_gender", gender).apply()
    }

    override fun getUserName(): String? {
        return prefs.getString("user_name", null)
    }
    override fun getUserGender(): String? {
        return prefs.getString("user_gender", null)
    }

    override fun saveChatHistory(messages: List<ChatMessage>) {
        val jsonString = Json.encodeToString(messages)
        prefs.edit().putString("chat_history", jsonString).apply()
    }

    override fun getChatHistory(): List<ChatMessage> {
        val jsonString = prefs.getString("chat_history", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun setModelDownloaded(downloaded: Boolean) {
        prefs.edit().putBoolean("model_downloaded", downloaded).apply()
    }

    override fun isModelDownloaded(): Boolean {
        return prefs.getBoolean("model_downloaded", false)
    }

    override fun savePersonality(personality: Personality) {
        prefs.edit().putString("personality", personality.name).apply()
    }

    override fun getPersonality(): Personality? {
        val name = prefs.getString("personality", null) ?: return null
        return try { Personality.valueOf(name) } catch (e: Exception) { null }
    }

    override fun saveVoiceId(voiceId: String) {
        prefs.edit().putString("voice_id", voiceId).apply()
    }

    override fun getVoiceId(): String? {
        return prefs.getString("voice_id", null)
    }

    override fun saveImportantFacts(facts: List<String>) {
        val jsonString = Json.encodeToString(facts)
        prefs.edit().putString("important_facts", jsonString).apply()
    }

    override fun getImportantFacts(): List<String> {
        val jsonString = prefs.getString("important_facts", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveReminders(reminders: List<org.example.project.model.Reminder>) {
        val jsonString = Json.encodeToString(reminders)
        prefs.edit().putString("reminders", jsonString).apply()
    }

    override fun getReminders(): List<org.example.project.model.Reminder> {
        val jsonString = prefs.getString("reminders", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun saveLastDailyGreetingDate(date: String) {
        prefs.edit().putString("last_daily_greeting", date).apply()
    }

    override fun getLastDailyGreetingDate(): String? {
        return prefs.getString("last_daily_greeting", null)
    }

    override fun saveEventRules(rules: List<org.example.project.model.EventRule>) {
        val jsonString = Json.encodeToString(rules)
        prefs.edit().putString("event_rules", jsonString).apply()
    }

    override fun getEventRules(): List<org.example.project.model.EventRule> {
        val jsonString = prefs.getString("event_rules", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun setTutorialCompleted(completed: Boolean) {
        prefs.edit().putBoolean("tutorial_completed", completed).apply()
    }

    override fun isTutorialCompleted(): Boolean {
        return prefs.getBoolean("tutorial_completed", false)
    }

    override fun setAlwaysListening(enabled: Boolean) {
        prefs.edit().putBoolean("always_listening", enabled).apply()
    }

    override fun isAlwaysListening(): Boolean {
        return prefs.getBoolean("always_listening", false)
    }

    override fun setAssistantSilenceTimeout(seconds: Int) {
        prefs.edit().putInt("assistant_silence_timeout", seconds).apply()
    }

    override fun getAssistantSilenceTimeout(): Int {
        return prefs.getInt("assistant_silence_timeout", 10)
    }
}

class MainActivity : ComponentActivity() {
    
    private val isAssistantLaunchState = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isAssistantLaunchState.value = intent.getBooleanExtra("assistant_launch", false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        isAssistantLaunchState.value = intent.getBooleanExtra("assistant_launch", false)

        // Make context available to expect/actual platform functions (e.g. openSystemSettings)
        appContext = applicationContext

        val settingsManager = AndroidSettingsManager(applicationContext)

        setContent {
            val context = LocalContext.current
            
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.RECORD_AUDIO] == false) {
                    println("Audio permission denied. STT will not work.")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == false
                ) {
                    println("Bluetooth permission denied. Bluetooth event names may not work.")
                }
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                    println("Location permission denied. WiFi event names may not work.")
                }
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionsToRequest += Manifest.permission.BLUETOOTH_CONNECT
                }

                val missingPermissions = permissionsToRequest.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                }
            }
            
            // Note: Since these managers are tied to context, they are created 
            // inside setContent and remember-ed to survive recompositions without leaks.
            val speechToTextManager = remember { AndroidSpeechToTextManager(context) }
            val piperSynthesizer = remember { AndroidPiperSynthesizer(context) }
            val modelDownloader = remember { org.example.project.audio.AndroidModelDownloader(context) }

            DisposableEffect(Unit) {
                onDispose {
                    speechToTextManager.destroy()
                    piperSynthesizer.destroy()
                }
            }

            App(
                settingsManager = settingsManager,
                speechToTextManager = speechToTextManager,
                piperSynthesizer = piperSynthesizer,
                modelDownloader = modelDownloader,
                isAssistantLaunch = isAssistantLaunchState.value
            )
        }
    }
}

// We can mock the preview
class MockSettingsManager : SettingsManager {
    override fun saveApiKey(apiKey: String) {}
    override fun getApiKey(): String? = null
    override fun saveUserDetails(name: String, gender: String) {}
    override fun getUserName(): String? = null
    override fun getUserGender(): String? = null
    override fun saveChatHistory(messages: List<ChatMessage>) {}
    override fun getChatHistory(): List<ChatMessage> = emptyList()
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

class MockSpeechToTextManager : org.example.project.audio.SpeechToTextManager {
    override fun startListening(silenceThresholdMs: Long, volumeThreshold: Float, onPartialResult: (String) -> Unit, onResult: (String) -> Unit, onAudioLevel: (Float) -> Unit, onError: (String) -> Unit) {}
    override fun stopListening() {}
    override fun destroy() {}
}

class MockPiperSynthesizer : org.example.project.audio.PiperSynthesizer {
    override fun init(modelPath: String, tokensPath: String, dataPath: String) {}
    override fun speak(text: String, onStart: () -> Unit, onComplete: () -> Unit) {}
    override fun stop() {}
    override fun destroy() {}
}

class MockModelDownloader : org.example.project.audio.ModelDownloader {
    override suspend fun downloadModel(voiceId: String, onProgress: (Float) -> Unit): Boolean = true
    override suspend fun downloadHotwordModel(onProgress: (Float) -> Unit): Boolean = true
    override fun isModelDownloaded(voiceId: String): Boolean = true
    override fun isHotwordModelDownloaded(): Boolean = true
    override fun getModelPath(voiceId: String): String = ""
    override fun getTokensPath(voiceId: String): String = ""
    override fun getDataPath(): String = ""
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        settingsManager = MockSettingsManager(),
        speechToTextManager = MockSpeechToTextManager(),
        piperSynthesizer = MockPiperSynthesizer(),
        modelDownloader = MockModelDownloader()
    )
}