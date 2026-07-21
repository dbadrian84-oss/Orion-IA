package org.example.project

import org.example.project.model.ChatMessage
import org.example.project.model.Personality
import org.example.project.model.Reminder

interface SettingsManager {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
    fun saveUserDetails(name: String, gender: String)
    fun getUserName(): String?
    fun getUserGender(): String?
    fun saveChatHistory(messages: List<ChatMessage>)
    fun getChatHistory(): List<ChatMessage>
    fun setModelDownloaded(downloaded: Boolean)
    fun isModelDownloaded(): Boolean
    fun savePersonality(personality: Personality)
    fun getPersonality(): Personality?
    fun saveVoiceId(voiceId: String)
    fun getVoiceId(): String?
    fun saveImportantFacts(facts: List<String>)
    fun getImportantFacts(): List<String>
    
    fun saveReminders(reminders: List<Reminder>)
    fun getReminders(): List<Reminder>
    
    fun saveLastDailyGreetingDate(date: String)
    fun getLastDailyGreetingDate(): String?
    
    fun saveEventRules(rules: List<org.example.project.model.EventRule>)
    fun getEventRules(): List<org.example.project.model.EventRule>
    
    fun setTutorialCompleted(completed: Boolean)
    fun isTutorialCompleted(): Boolean
    
    fun setAlwaysListening(enabled: Boolean)
    fun isAlwaysListening(): Boolean

    fun setAssistantSilenceTimeout(seconds: Int)
    fun getAssistantSilenceTimeout(): Int
}
