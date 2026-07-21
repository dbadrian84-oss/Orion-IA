package org.example.project.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*

/**
 * Android implementation of [SpeechToTextManager] using the system SpeechRecognizer.
 *
 * Uses Android's built-in continuous recognition. Monitors RMS audio level for the
 * BlobAnimator and implements VAD (Voice Activity Detection) via a coroutine timer:
 * if the RMS drops below [volumeThreshold] for [silenceThresholdMs] milliseconds, the
 * current partial result is committed and returned as a final result.
 */
class AndroidSpeechToTextManager(private val context: Context) : SpeechToTextManager {

    private var speechRecognizer: SpeechRecognizer? = null
    private var silenceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun startListening(
        silenceThresholdMs: Long,
        volumeThreshold: Float,
        onPartialResult: (String) -> Unit,
        onResult: (String) -> Unit,
        onAudioLevel: (Float) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconocimiento de voz no disponible en este dispositivo.")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        var lastPartialText = ""
        // RMS dB from Android is usually 0..10, normalize to 0..1f
        val rmsScale = 10f

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onRmsChanged(rmsdB: Float) {
                val level = (rmsdB / rmsScale).coerceIn(0f, 1f)
                onAudioLevel(level)

                // VAD: restart silence timer whenever the user is speaking
                if (level > volumeThreshold) {
                    silenceJob?.cancel()
                    silenceJob = scope.launch {
                        delay(silenceThresholdMs)
                        // Silence detected — commit the last partial result
                        if (lastPartialText.isNotBlank()) {
                            onResult(lastPartialText)
                        }
                        speechRecognizer?.stopListening()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                lastPartialText = text
                onPartialResult(text)
            }

            override fun onResults(results: Bundle?) {
                silenceJob?.cancel()
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                onAudioLevel(0f)
                onResult(text)
            }

            override fun onError(error: Int) {
                silenceJob?.cancel()
                onAudioLevel(0f)
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de red agotado"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No entendí lo que dijiste"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No detecté tu voz"
                    else -> "Error de reconocimiento ($error)"
                }
                // On speech timeout / no match, just silently return empty — don't disrupt UX
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    onError(message)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // No calling app overlay
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        speechRecognizer?.startListening(intent)
    }

    override fun stopListening() {
        silenceJob?.cancel()
        speechRecognizer?.stopListening()
    }

    override fun destroy() {
        silenceJob?.cancel()
        scope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
