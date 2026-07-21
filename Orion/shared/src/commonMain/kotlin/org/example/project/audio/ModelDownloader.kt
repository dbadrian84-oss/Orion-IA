package org.example.project.audio

interface ModelDownloader {
    suspend fun downloadModel(voiceId: String, onProgress: (Float) -> Unit): Boolean
    suspend fun downloadHotwordModel(onProgress: (Float) -> Unit): Boolean
    fun isModelDownloaded(voiceId: String): Boolean
    fun isHotwordModelDownloaded(): Boolean
    fun getModelPath(voiceId: String): String
    fun getTokensPath(voiceId: String): String
    fun getDataPath(): String
}
