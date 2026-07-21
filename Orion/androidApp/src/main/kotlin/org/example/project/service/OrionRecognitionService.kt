package org.example.project.service

import android.content.Intent
import android.speech.RecognitionService

class OrionRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Our STT is handled internally in the app's UI (AndroidSpeechToTextManager)
        // so we don't need a system-level recognizer here, but Android requires this service to exist.
    }

    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
