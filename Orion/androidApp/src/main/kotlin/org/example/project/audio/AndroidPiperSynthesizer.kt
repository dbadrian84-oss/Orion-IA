package org.example.project.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class AndroidPiperSynthesizer(private val context: Context) : PiperSynthesizer, TextToSpeech.OnInitListener {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var synthesisJob: Job? = null
    
    private var nativeTts: TextToSpeech? = null
    private var useNativeTts = false
    private var ttsReady = false
    private var pendingOnComplete: (() -> Unit)? = null
    private var pendingOnStart: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            nativeTts?.language = Locale("es", "ES")
            ttsReady = true
        } else {
            println("AndroidPiperSynthesizer: Native TTS failed to initialize.")
        }
    }

    override fun init(modelPath: String, tokensPath: String, dataPath: String) {
        destroy() // Clean up previous instance if any

        if (modelPath == "android_native") {
            useNativeTts = true
            nativeTts = TextToSpeech(context, this)
            return
        }

        useNativeTts = false

        if (!File(modelPath).exists()) {
            println("AndroidPiperSynthesizer: Model file not found at $modelPath")
            return
        }

        try {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelPath,
                        tokens = tokensPath,
                        dataDir = dataPath,
                        lexicon = "",
                        dictDir = ""
                    ),
                    provider = "cpu",
                    numThreads = 2,
                    debug = false
                ),
                ruleFsts = "",
                maxNumSentences = 10
            )
            tts = OfflineTts(assetManager = null, config = config)
            println("AndroidPiperSynthesizer: Initialized successfully.")
        } catch (e: Throwable) {
            e.printStackTrace()
            println("AndroidPiperSynthesizer: Failed to init Piper, will use native TTS as fallback")
            // Fallback: use native TTS instead of crashing
            useNativeTts = true
            if (nativeTts == null) nativeTts = TextToSpeech(context, this)
        }
    }

    override fun speak(text: String, onStart: () -> Unit, onComplete: () -> Unit) {
        if (useNativeTts) {
            if (!ttsReady || nativeTts == null) {
                onComplete()
                return
            }
            pendingOnStart = onStart
            pendingOnComplete = onComplete
            
            nativeTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) { pendingOnStart?.invoke() }
                }
                override fun onDone(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) { pendingOnComplete?.invoke() }
                }
                override fun onError(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) { pendingOnComplete?.invoke() }
                }
            })
            
            val params = android.os.Bundle()
            nativeTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "orion_tts")
            return
        }

        val currentTts = tts
        if (currentTts == null) {
            println("AndroidPiperSynthesizer: tts is null, cannot speak.")
            onComplete()
            return
        }

        stop() // Stop any ongoing speech

        synthesisJob = scope.launch {
            try {
                // Generate audio with default sid=0 and speed=1.0f
                val generatedAudio = currentTts.generate(text = text, sid = 0, speed = 1.0f)
                if (generatedAudio.samples.isEmpty()) {
                    withContext(Dispatchers.Main) { onComplete() }
                    return@launch
                }

                val samples = generatedAudio.samples
                val sampleRate = generatedAudio.sampleRate

                // Convert float array to 16-bit PCM short array for AudioTrack
                val pcmData = ShortArray(samples.size)
                for (i in samples.indices) {
                    val s = (samples[i] * 32767).toInt()
                    pcmData[i] = s.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufSize = maxOf(minBufSize, pcmData.size * 2)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                withContext(Dispatchers.Main) { onStart() }
                track.play()

                // Write all audio data at once
                track.write(pcmData, 0, pcmData.size)

                // Calculate how long the audio will take to play and wait for it
                val durationMs = (pcmData.size.toLong() * 1000L) / sampleRate
                kotlinx.coroutines.delay(durationMs + 300L) // +300ms safety margin

                if (isActive) {
                    try { track.stop() } catch (_: Exception) {}
                }
                try { track.release() } catch (_: Exception) {}
                audioTrack = null

                withContext(Dispatchers.Main) { onComplete() }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    override fun stop() {
        if (useNativeTts) {
            nativeTts?.stop()
        }
        
        synthesisJob?.cancel()
        audioTrack?.let {
            if (it.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    it.pause()
                    it.flush()
                } catch (e: Exception) { e.printStackTrace() }
            }
            it.release()
        }
        audioTrack = null
    }

    override fun destroy() {
        try { stop() } catch (_: Throwable) {}
        try { nativeTts?.shutdown() } catch (_: Throwable) {}
        nativeTts = null
        try { tts?.release() } catch (_: Throwable) {}
        tts = null
    }
}
