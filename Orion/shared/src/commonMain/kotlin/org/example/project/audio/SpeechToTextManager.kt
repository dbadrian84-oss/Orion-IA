package org.example.project.audio

/**
 * Shared interface for Speech-To-Text.
 * Platform-specific implementations handle actual recording and recognition.
 */
interface SpeechToTextManager {
    /**
     * Start listening. [onPartialResult] fires with interim text as the user speaks.
     * [onResult] fires with the final transcribed text after silence is detected.
     * [onAudioLevel] fires with a 0f..1f level for the BlobAnimator.
     * [onError] fires on any error.
     */
    fun startListening(
        silenceThresholdMs: Long = 2000L,
        volumeThreshold: Float = 0.10f,
        onPartialResult: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onAudioLevel: (Float) -> Unit = {},
        onError: (String) -> Unit = {}
    )

    /** Stop listening immediately and discard any pending result. */
    fun stopListening()

    /** Release all resources. Call in onDestroy / composable disposal. */
    fun destroy()
}
