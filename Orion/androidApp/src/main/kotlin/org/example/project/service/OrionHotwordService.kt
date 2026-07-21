package org.example.project.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import org.example.project.MainActivity
import org.example.project.audio.VoskModelDownloader
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class OrionHotwordService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isListening = false // el hilo de escucha sigue vivo
    @Volatile private var isPaused = false // pero no debe tocar el micro mientras esté pausado
    private var voskModel: Model? = null
    private var recognizer: Recognizer? = null
    private var listenerThread: Thread? = null
    private var floatingBubbleManager: FloatingBubbleManager? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Objeto de bloqueo para sincronizar pausas/reanudaciones del AudioRecord
    private val micLock = Object()

    companion object {
        const val CHANNEL_ID = "orion_hotword"
        const val NOTIF_ID = 99
        const val ACTION_STOP = "org.example.project.STOP_HOTWORD"

        // Nueva acción: la Activity de conversación la envía cuando termina,
        // para que el hotword vuelva a escuchar.
        const val ACTION_RESUME_HOTWORD = "org.example.project.RESUME_HOTWORD"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val resumeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_RESUME_HOTWORD) {
                    resumeHotwordListening()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter(ACTION_RESUME_HOTWORD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(resumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(resumeReceiver, filter)
        }

        mainHandler.post {
            floatingBubbleManager =
                FloatingBubbleManager(this) {
                    // Usuario cerró la burbuja
                    stopSelf()
                }
            floatingBubbleManager?.show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        startHotwordDetection()
        return START_STICKY
    }

    // ------------------------------------------------------------------
    // Ciclo de vida del micrófono del hotword
    // ------------------------------------------------------------------

    /**
     * Crea (o recrea) el AudioRecord y empieza a grabar. Debe llamarse SIEMPRE dentro de micLock.
     */
    private fun openAudioRecordLocked() {
        val bufferSize =
            AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

        audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
            )

        val audioSessionId = audioRecord?.audioSessionId
        if (audioSessionId != null) {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioSessionId)?.enabled = true
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.enabled = true
            }
        }

        audioRecord?.startRecording()
    }

    /**
     * Libera por completo el AudioRecord (stop + release) para que otro consumidor (la
     * conversación) pueda tomar el micrófono sin contención. NO destruye el modelo/recognizer de
     * Vosk: son caros de recrear y no usan el hardware de audio directamente.
     */
    private fun closeAudioRecordLocked() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
            // puede lanzar si nunca llegó a iniciar grabación; lo ignoramos
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    /** Se llama justo antes de lanzar la conversación. */
    private fun pauseHotwordListening() {
        synchronized(micLock) {
            if (isPaused) return
            isPaused = true
            closeAudioRecordLocked()
        }
        mainHandler.post { floatingBubbleManager?.isMicActive = false }
    }

    /** Se llama cuando la conversación termina y hay que reanudar la escucha. */
    private fun resumeHotwordListening() {
        synchronized(micLock) {
            if (!isPaused) return
            try {
                openAudioRecordLocked()
            } catch (e: Exception) {
                e.printStackTrace()
                // Si falla el resume (p.ej. micro aún ocupado), reintenta en 500ms
                mainHandler.postDelayed({ resumeHotwordListening() }, 500)
                return
            }
            isPaused = false
        }
    }

    private fun startHotwordDetection() {
        if (isListening) return
        isListening = true

        listenerThread =
            Thread {
                    try {
                        val downloader = VoskModelDownloader(this)
                        if (!downloader.isModelReady()) {
                            stopSelf()
                            return@Thread
                        }

                        voskModel = Model(downloader.getModelPath())
                        recognizer = Recognizer(voskModel, 16000.0f)

                        synchronized(micLock) { openAudioRecordLocked() }

                        val bufferSize =
                            AudioRecord.getMinBufferSize(
                                16000,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                            )
                        val buffer = ShortArray(bufferSize)

                        while (isListening) {
                            // Si está pausado (conversación en curso), no tocar el micro.
                            // Esperamos activamente en intervalos cortos hasta que se reanude.
                            if (isPaused) {
                                Thread.sleep(150)
                                continue
                            }

                            // Pausa si hay una llamada activa
                            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                            if (tm.callState != TelephonyManager.CALL_STATE_IDLE) {
                                Thread.sleep(1000)
                                continue
                            }

                            val read =
                                synchronized(micLock) {
                                    // Puede haberse cerrado justo entre el check de arriba y aquí
                                    if (isPaused || audioRecord == null) return@synchronized -1
                                    audioRecord?.read(buffer, 0, buffer.size) ?: -1
                                }
                            if (read <= 0) continue

                            val byteBuffer = ByteArray(read * 2)
                            for (i in 0 until read) {
                                byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] =
                                    ((buffer[i].toInt() shr 8) and 0xFF).toByte()
                            }

                            // RMS para animar la burbuja
                            var sum = 0.0
                            for (i in 0 until read) sum += buffer[i].toLong() * buffer[i].toLong()
                            val rms = Math.sqrt(sum / read)
                            val isVoiceActive = rms > 500.0
                            mainHandler.post { floatingBubbleManager?.isMicActive = isVoiceActive }

                            if (recognizer?.acceptWaveForm(byteBuffer, byteBuffer.size) == true) {
                                val result = recognizer?.result ?: continue
                                val text =
                                    try {
                                        JSONObject(result).getString("text")
                                    } catch (e: Exception) {
                                        ""
                                    }

                                if (text.contains("orion", ignoreCase = true)) {
                                    launchOrionAssistant()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        synchronized(micLock) { closeAudioRecordLocked() }
                    }
                }
                .also { it.start() }
    }

    // ------------------------------------------------------------------
    // Lanzamiento de la conversación como overlay, no como app a pantalla completa
    // ------------------------------------------------------------------

    private fun launchOrionAssistant() {
        // 1. Suelta el micrófono ANTES de que la conversación intente usarlo.
        pauseHotwordListening()

        // 2. Lanza MainActivity con el tema translúcido "OrionOverlay" (ver manifest)
        //    para que se vea como un diálogo flotante sobre la app actual,
        //    no como una app nueva a pantalla completa.
        val intent =
            Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
                putExtra("assistant_launch", true)
                putExtra(
                    "assistant_overlay_mode",
                    true,
                ) // MainActivity debe usarlo para mostrar solo el panel de chat
            }
        startActivity(intent)
    }

    override fun onDestroy() {
        isListening = false
        isPaused = false
        listenerThread?.interrupt()
        synchronized(micLock) { closeAudioRecordLocked() }
        recognizer?.close()
        voskModel?.close()
        try {
            unregisterReceiver(resumeReceiver)
        } catch (_: Exception) {}
        mainHandler.post {
            floatingBubbleManager?.hide()
            floatingBubbleManager = null
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val stopIntent =
            Intent(this, OrionHotwordService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Orion está escuchando")
            .setContentText("Di \"Orion\" para activar el asistente")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "Detener", stopPi).build())
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        "Orion - Escucha de activación",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                    .apply {
                        description = "Escucha en segundo plano la palabra 'Orion'"
                        setShowBadge(false)
                    }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
