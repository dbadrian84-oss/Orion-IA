package org.example.project.audio

/**
 * Shared interface for Text-To-Speech using Piper (Sherpa-ONNX).
 * Platform-specific implementations handle the actual model loading and audio generation.
 */
interface PiperSynthesizer {
    /**
     * Initialize the synthesizer with the given model paths.
     * @param modelPath Path or asset identifier for the .onnx model file.
     * @param tokensPath Path or asset identifier for the tokens file.
     * @param dataPath Path or asset identifier for the data file (optional, depends on model).
     */
    fun init(modelPath: String, tokensPath: String, dataPath: String = "")

    /**
     * Synthesize text to speech and play it.
     * @param text The text to synthesize.
     * @param onStart Called when audio playback starts.
     * @param onComplete Called when audio playback finishes.
     */
    fun speak(text: String, onStart: () -> Unit = {}, onComplete: () -> Unit = {})

    /** Stop any ongoing speech playback. */
    fun stop()

    /** Release resources. */
    fun destroy()
}
