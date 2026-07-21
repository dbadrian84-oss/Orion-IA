package org.example.project.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.example.project.audio.ModelDownloader
import org.example.project.audio.PiperSynthesizer
import org.example.project.audio.SpeechToTextManager
import org.example.project.model.Personality
import org.example.project.model.toGeminiContent
import org.example.project.network.Content
import org.example.project.network.GeminiClient
import org.example.project.network.Part
import org.example.project.ui.components.BlobAnimator
import org.example.project.ui.theme.DeepBlack
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.NeonBlue

enum class VoiceState {
    IDLE, // Blob breathes slowly, waiting
    LISTENING, // User is speaking, blob reacts to mic audio
    PROCESSING, // Waiting for Gemini response, blob pulses
    SPEAKING, // Orion is speaking via TTS, blob reacts to audio output
}

@Composable
fun VoiceScreen(
    apiKey: String,
    userName: String,
    userGender: String,
    personality: Personality,
    voiceId: String,
    settingsManager: org.example.project.SettingsManager,
    speechToTextManager: SpeechToTextManager,
    piperSynthesizer: PiperSynthesizer,
    modelDownloader: ModelDownloader,
    geminiClient: GeminiClient,
    isAssistantLaunch: Boolean = false,
    onNavigateToChat: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onSuspend: () -> Unit = {},
) {
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var audioLevel by remember { mutableStateOf(0f) }
    var showConfig by remember { mutableStateOf(false) }
    var silenceThresholdSeconds by remember { mutableStateOf(2f) }
    var silenceVolumeThreshold by remember { mutableStateOf(0.10f) }
    var statusText by remember { mutableStateOf("Toca el micrófono para empezar") }
    var isAutoListening by remember { mutableStateOf(isAssistantLaunch) }

    // For assistant mode timeout (suspension)
    val assistantSilenceTimeoutMs = settingsManager.getAssistantSilenceTimeout() * 1000L
    var lastSpeechTime by remember { mutableStateOf(org.example.project.getCurrentTimeMillis()) }
    var lastActiveTime by remember { mutableStateOf(org.example.project.getCurrentTimeMillis()) }

    val scope = rememberCoroutineScope()
    val showListeningButton =
        voiceState == VoiceState.LISTENING || (isAutoListening && voiceState == VoiceState.IDLE)

    // LaunchedEffect moved below handleSpeechResult to avoid forward reference

    // In-memory multi-turn history: seed with last 10 stored messages
    val conversationHistory = remember {
        mutableStateListOf<Content>().also { list ->
            settingsManager.getChatHistory().takeLast(10).forEach { msg ->
                list.add(msg.toGeminiContent())
            }
        }
    }

    val handleSpeechResult: (String) -> Unit = { resultText ->
        lastSpeechTime = org.example.project.getCurrentTimeMillis()
        if (resultText.isNotBlank()) {
            voiceState = VoiceState.PROCESSING
            statusText = "Pensando..."

            // Call Gemini
            scope.launch {
                val todayStr = org.example.project.getCurrentDate()
                val lastGreeting = settingsManager.getLastDailyGreetingDate()
                val greetingRule =
                    if (lastGreeting != todayStr) {
                        "Es la primera vez que hablas con el usuario hoy. Salúdalo amistosamente."
                    } else {
                        "Ya has hablado con el usuario hoy. Continúa la conversación natural, NO saludes de nuevo."
                    }

                val systemInstruction =
                    "Eres una Inteligencia Artificial llamada Orion. El usuario se llama $userName y su género es $userGender. Hoy es $todayStr. Hablas en español de España. $greetingRule REGLA DE ORO: Tus respuestas deben ser EXTREMADAMENTE BREVES y conversacionales, como si estuvieras hablando en persona. MÁXIMO 1 o 2 oraciones para preguntas simples o saludos. Si es una duda compleja, ve directo al grano sin introducciones largas. NUNCA sueltes párrafos largos ni listas a menos que se te pida explícitamente 'explícame en detalle'. No repitas el nombre del usuario constantemente.\n${personality.promptPrefix}"

                val reminderRules =
                    "REGLA ESTRICTA DE RECORDATORIOS: Si el usuario pide crear un recordatorio, responde ÚNICAMENTE con ||RECORDATORIO:AAAA-MM-DD|Texto|| usando la fecha ISO exacta. Hoy es $todayStr. Si pregunta por sus recordatorios, responde ÚNICAMENTE con [LEER_RECORDATORIOS]."
                val appLaunchRules =
                    " REGLA ESTRICTA DE APERTURA DE APPS: Si el usuario pide abrir o lanzar una aplicación, responde ÚNICAMENTE con ||ABRIR_APP:Nombre de la app||. No añadas explicación."

                // Daily greeting logic
                var dailyGreetingContext = ""
                if (lastGreeting != todayStr) {
                    val todaysReminders =
                        settingsManager.getReminders().filter {
                            org.example.project.model.isReminderOnDate(it, todayStr) &&
                                !it.isCompleted
                        }
                    if (todaysReminders.isNotEmpty()) {
                        dailyGreetingContext =
                            "\nIMPORTANTE: Es la primera vez que hablas con el usuario hoy. Tiene estos recordatorios para HOY: " +
                                todaysReminders.joinToString("; ") { it.text } +
                                ". Menciónalos sutilmente."
                    }
                }

                val importantFacts = settingsManager.getImportantFacts()
                val factsContext =
                    if (importantFacts.isNotEmpty()) {
                        "\nDatos importantes sobre el usuario:\n" +
                            importantFacts.mapIndexed { i, f -> "${i+1}. $f" }.joinToString("\n")
                    } else ""

                val fullSystemInstruction =
                    systemInstruction +
                        reminderRules +
                        appLaunchRules +
                        factsContext +
                        dailyGreetingContext

                val stream =
                    geminiClient.generateStreamWithHistory(
                        systemInstruction = fullSystemInstruction,
                        history = conversationHistory.toList(),
                        userMessage = resultText,
                    )

                val fullResponse = StringBuilder()
                var isHiddenCommand = false

                stream
                    .catch { e ->
                        statusText = e.message ?: "Error"
                        voiceState = VoiceState.IDLE
                    }
                    .collect { chunk ->
                        fullResponse.append(chunk)
                        val textSoFar = fullResponse.toString()

                        if (textSoFar.startsWith("||") || textSoFar.startsWith("[")) {
                            isHiddenCommand = true
                            statusText = "Procesando..."
                        } else {
                            statusText = fullResponse.toString()
                        }
                    }

                val finalResponse = fullResponse.toString().trim()
                if (finalResponse.isEmpty()) {
                    if (isAutoListening) {
                        voiceState = VoiceState.LISTENING
                        statusText = "Escuchando..."
                    } else {
                        voiceState = VoiceState.IDLE
                        statusText = "Toca el micrófono para hablar"
                    }
                    return@launch
                }

                if (finalResponse.startsWith("||ABRIR_APP:") && finalResponse.endsWith("||")) {
                    val appQuery =
                        finalResponse.removePrefix("||ABRIR_APP:").removeSuffix("||").trim()
                    val launchedApp = org.example.project.openInstalledApp(appQuery)
                    val appNotFoundResponses =
                        listOf(
                            "No encuentro una app llamada $appQuery.",
                            "No veo ninguna app parecida a $appQuery.",
                            "No he encontrado esa app en el móvil.",
                            "No me aparece ninguna aplicación con ese nombre.",
                            "No encuentro $appQuery entre tus apps instaladas.",
                            "No he podido localizar esa app.",
                            "No veo una coincidencia clara para $appQuery.",
                            "No parece que tengas una app llamada así.",
                            "No he encontrado nada suficientemente parecido a $appQuery.",
                            "No consigo abrirla porque no encuentro esa aplicación.",
                        )
                    val assistantText =
                        if (launchedApp != null) "Abriendo $launchedApp."
                        else appNotFoundResponses.random()
                    statusText = assistantText

                    val now = org.example.project.getCurrentTimeMillis()
                    val history = settingsManager.getChatHistory().toMutableList()
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = true,
                            text = resultText,
                            timestamp = now - 1,
                        )
                    )
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = false,
                            text = assistantText,
                            timestamp = now,
                        )
                    )
                    settingsManager.saveChatHistory(history)

                    conversationHistory.add(
                        Content(role = "user", parts = listOf(Part(resultText)))
                    )
                    conversationHistory.add(
                        Content(role = "model", parts = listOf(Part(assistantText)))
                    )
                    while (conversationHistory.size > 20) conversationHistory.removeAt(0)

                    if (lastGreeting != todayStr)
                        settingsManager.saveLastDailyGreetingDate(todayStr)
                    voiceState = VoiceState.SPEAKING
                    piperSynthesizer.speak(
                        text = assistantText,
                        onStart = { audioLevel = 0.6f },
                        onComplete = {
                            if (isAutoListening) {
                                voiceState = VoiceState.LISTENING
                                statusText = "Escuchando..."
                            } else {
                                voiceState = VoiceState.IDLE
                                statusText = "Toca el micrófono para hablar"
                            }
                        },
                    )
                } else if (
                    finalResponse.startsWith("||RECORDATORIO:") && finalResponse.endsWith("||")
                ) {
                    val content = finalResponse.removePrefix("||RECORDATORIO:").removeSuffix("||")
                    val parts = content.split("|", limit = 2)
                    if (parts.size == 2) {
                        val date = org.example.project.model.normalizeReminderDate(parts[0])
                        val text = parts[1]
                        val newReminder =
                            org.example.project.model.Reminder(
                                id = org.example.project.getCurrentTimeMillis().toString(),
                                date = date,
                                text = text,
                            )
                        settingsManager.saveReminders(settingsManager.getReminders() + newReminder)

                        val confirmStream =
                            geminiClient.generateStreamWithHistory(
                                systemInstruction = fullSystemInstruction,
                                history = conversationHistory.toList(),
                                userMessage =
                                    "El usuario pidió guardar el recordatorio '$text' para el día '$date' y ya está guardado. Confírmale brevemente.",
                            )
                        val confirmResponse = StringBuilder()
                        confirmStream.collect { chunk ->
                            confirmResponse.append(chunk)
                            statusText = confirmResponse.toString()
                        }

                        // Save to chat history
                        val now = org.example.project.getCurrentTimeMillis()
                        val history = settingsManager.getChatHistory().toMutableList()
                        history.add(
                            org.example.project.model.ChatMessage(
                                isUser = true,
                                text = resultText,
                                timestamp = now - 1,
                            )
                        )
                        history.add(
                            org.example.project.model.ChatMessage(
                                isUser = false,
                                text = confirmResponse.toString(),
                                timestamp = now,
                            )
                        )
                        settingsManager.saveChatHistory(history)

                        // Update multi-turn history
                        conversationHistory.add(
                            Content(role = "user", parts = listOf(Part(resultText)))
                        )
                        conversationHistory.add(
                            Content(
                                role = "model",
                                parts = listOf(Part(confirmResponse.toString())),
                            )
                        )
                        while (conversationHistory.size > 20) conversationHistory.removeAt(0)

                        if (lastGreeting != todayStr)
                            settingsManager.saveLastDailyGreetingDate(todayStr)
                        voiceState = VoiceState.SPEAKING
                        piperSynthesizer.speak(
                            text = confirmResponse.toString(),
                            onStart = { audioLevel = 0.6f },
                            onComplete = {
                                if (isAutoListening) {
                                    voiceState = VoiceState.LISTENING
                                    statusText = "Escuchando..."
                                } else {
                                    voiceState = VoiceState.IDLE
                                    statusText = "Toca el micrófono para hablar"
                                }
                            },
                        )
                    } else {
                        voiceState = VoiceState.IDLE
                    }
                } else if (finalResponse.startsWith("[LEER_RECORDATORIOS]")) {
                    val pending = settingsManager.getReminders().filter { !it.isCompleted }
                    val remindersText =
                        if (pending.isEmpty()) "No hay recordatorios pendientes."
                        else
                            pending.joinToString(", ") {
                                "Día ${org.example.project.model.formatReminderDateForUser(it.date)}: ${it.text}"
                            }

                    val readStream =
                        geminiClient.generateStreamWithHistory(
                            systemInstruction = fullSystemInstruction,
                            history = conversationHistory.toList(),
                            userMessage =
                                "El usuario quiere saber sus recordatorios. La base de datos dice: $remindersText. Díselo de forma natural.",
                        )
                    val readResponse = StringBuilder()
                    readStream.collect { chunk ->
                        readResponse.append(chunk)
                        statusText = readResponse.toString()
                    }

                    // Save to chat history
                    val now = org.example.project.getCurrentTimeMillis()
                    val history = settingsManager.getChatHistory().toMutableList()
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = true,
                            text = resultText,
                            timestamp = now - 1,
                        )
                    )
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = false,
                            text = readResponse.toString(),
                            timestamp = now,
                        )
                    )
                    settingsManager.saveChatHistory(history)

                    // Update multi-turn history
                    conversationHistory.add(
                        Content(role = "user", parts = listOf(Part(resultText)))
                    )
                    conversationHistory.add(
                        Content(role = "model", parts = listOf(Part(readResponse.toString())))
                    )
                    while (conversationHistory.size > 20) conversationHistory.removeAt(0)

                    if (lastGreeting != todayStr)
                        settingsManager.saveLastDailyGreetingDate(todayStr)
                    voiceState = VoiceState.SPEAKING
                    piperSynthesizer.speak(
                        text = readResponse.toString(),
                        onStart = { audioLevel = 0.6f },
                        onComplete = {
                            if (isAutoListening) {
                                voiceState = VoiceState.LISTENING
                                statusText = "Escuchando..."
                            } else {
                                voiceState = VoiceState.IDLE
                                statusText = "Toca el micrófono para hablar"
                            }
                        },
                    )
                } else {
                    // Save to chat history
                    val now = org.example.project.getCurrentTimeMillis()
                    val history = settingsManager.getChatHistory().toMutableList()
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = true,
                            text = resultText,
                            timestamp = now - 1,
                        )
                    )
                    history.add(
                        org.example.project.model.ChatMessage(
                            isUser = false,
                            text = finalResponse,
                            timestamp = now,
                        )
                    )
                    settingsManager.saveChatHistory(history)

                    // Update multi-turn history
                    conversationHistory.add(
                        Content(role = "user", parts = listOf(Part(resultText)))
                    )
                    conversationHistory.add(
                        Content(role = "model", parts = listOf(Part(finalResponse)))
                    )
                    while (conversationHistory.size > 20) conversationHistory.removeAt(0)

                    if (lastGreeting != todayStr)
                        settingsManager.saveLastDailyGreetingDate(todayStr)
                    voiceState = VoiceState.SPEAKING
                    piperSynthesizer.speak(
                        text = finalResponse,
                        onStart = { audioLevel = 0.6f },
                        onComplete = {
                            if (isAutoListening) {
                                voiceState = VoiceState.LISTENING
                                statusText = "Escuchando..."
                            } else {
                                voiceState = VoiceState.IDLE
                                statusText = "Toca el micrófono para hablar"
                            }
                        },
                    )
                }
            }
        } else {
            voiceState = VoiceState.IDLE
        }
    }

    // Assistant silence timeout
    LaunchedEffect(isAutoListening, voiceState) {
        if (isAutoListening && voiceState == VoiceState.LISTENING) {
            lastActiveTime =
                org.example.project.getCurrentTimeMillis() // Reset when entering LISTENING
            while (true) {
                kotlinx.coroutines.delay(1000)
                val now = org.example.project.getCurrentTimeMillis()
                val timeoutMs = settingsManager.getAssistantSilenceTimeout() * 1000L
                if (now - lastActiveTime > timeoutMs) {
                    // Timeout!
                    isAutoListening = false
                    voiceState = VoiceState.IDLE
                    statusText = "Modo suspensión. Di 'Orion' de nuevo."
                    speechToTextManager.stopListening()
                    break
                }
            }
        }
    }

    // Auto-init TTS on first load
    LaunchedEffect(voiceId) {
        if (voiceId == "android_native") {
            piperSynthesizer.init(modelPath = "android_native", tokensPath = "")
        } else {
            val modelPath = modelDownloader.getModelPath(voiceId)
            val tokensPath = modelDownloader.getTokensPath(voiceId)
            val dataPath = modelDownloader.getDataPath()
            println(
                "VoiceScreen: init TTS modelPath=$modelPath tokensPath=$tokensPath dataPath=$dataPath"
            )
            if (modelPath.isNotEmpty() && tokensPath.isNotEmpty()) {
                piperSynthesizer.init(
                    modelPath = modelPath,
                    tokensPath = tokensPath,
                    dataPath = dataPath,
                )
            } else {
                // Fallback to native TTS if model not found
                println("VoiceScreen: Piper model not found, falling back to native TTS")
                piperSynthesizer.init(modelPath = "android_native", tokensPath = "")
            }
        }
    }

    // Trigger listening when invoked as phone assistant (via button or hotword)
    LaunchedEffect(isAssistantLaunch) {
        if (isAssistantLaunch) {
            isAutoListening = true
            if (voiceState != VoiceState.LISTENING) {
                voiceState = VoiceState.LISTENING
                statusText = "Escuchando..."
                speechToTextManager.startListening(
                    silenceThresholdMs = (silenceThresholdSeconds * 1000).toLong(),
                    volumeThreshold = silenceVolumeThreshold,
                    onPartialResult = {
                        statusText = it
                        lastSpeechTime = org.example.project.getCurrentTimeMillis()
                    },
                    onResult = handleSpeechResult,
                    onAudioLevel = {
                        audioLevel = it
                        if (it > silenceVolumeThreshold)
                            lastActiveTime = org.example.project.getCurrentTimeMillis()
                    },
                    onError = {
                        statusText = it
                        voiceState = VoiceState.IDLE
                    },
                )
            }
        }
    }

    // Cleanup when leaving
    DisposableEffect(Unit) {
        onDispose {
            speechToTextManager.stopListening()
            piperSynthesizer.stop()
        }
    }

    // Auto-suspend if no speech for assistantSilenceTimeoutMs
    LaunchedEffect(voiceState, lastSpeechTime, isAssistantLaunch) {
        if (voiceState == VoiceState.LISTENING && isAssistantLaunch) {
            kotlinx.coroutines.delay(assistantSilenceTimeoutMs)
            // If we reach here, we timed out
            voiceState = VoiceState.IDLE
            statusText = "Suspendido por inactividad"
            isAutoListening = false
            speechToTextManager.stopListening()
            piperSynthesizer.stop()
            onSuspend()
        }
    }

    // Auto-restart STT whenever voiceState becomes LISTENING (continuous mode)
    LaunchedEffect(voiceState, isAutoListening) {
        when (voiceState) {
            VoiceState.IDLE -> {
                audioLevel = 0f
                if (isAutoListening) {
                    // Small delay before bouncing back to avoid tight loop
                    kotlinx.coroutines.delay(500)
                    voiceState = VoiceState.LISTENING
                } else {
                    statusText = "Toca el micrófono para empezar"
                }
            }
            VoiceState.LISTENING -> {
                statusText = "Escuchando..."
                if (isAutoListening) {
                    // Small delay before re-starting to prevent tight error loops
                    kotlinx.coroutines.delay(300)
                    speechToTextManager.startListening(
                        silenceThresholdMs = (silenceThresholdSeconds * 1000).toLong(),
                        volumeThreshold = silenceVolumeThreshold,
                        onPartialResult = { partial ->
                            if (voiceState == VoiceState.SPEAKING) {
                                piperSynthesizer.stop()
                                voiceState = VoiceState.LISTENING
                            }
                            statusText = partial
                            lastActiveTime = org.example.project.getCurrentTimeMillis()
                            lastSpeechTime = org.example.project.getCurrentTimeMillis()
                        },
                        onResult = handleSpeechResult,
                        onAudioLevel = {
                            audioLevel = it
                            if (it > silenceVolumeThreshold)
                                lastActiveTime = org.example.project.getCurrentTimeMillis()
                        },
                        onError = { err ->
                            // In auto-listen mode, go to IDLE briefly — the IDLE branch will bounce
                            // us back
                            if (isAutoListening) {
                                statusText = "Escuchando..."
                                voiceState = VoiceState.IDLE
                            } else {
                                statusText = err
                                voiceState = VoiceState.IDLE
                            }
                        },
                    )
                }
            }
            VoiceState.PROCESSING -> {
                audioLevel = 0.2f
                statusText = "Pensando..."
            }
            VoiceState.SPEAKING -> {
                statusText = "Orion habla..."
                // Do NOT start listening while speaking — the microphone would pick up
                // Orion's own voice and immediately call stop(), cutting audio short.
                // The transition back to LISTENING happens in piperSynthesizer.speak's onComplete.
            }
        }
    }

    FuturisticBackground {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Top Left: Chat button ──
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .padding(top = 52.dp, start = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141428).copy(alpha = 0.8f))
                        .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                TextButton(onClick = onNavigateToChat) {
                    Text("💬", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Chat", color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ── Top Right: Config button ──
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(top = 52.dp, end = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141428).copy(alpha = 0.8f))
                        .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                TextButton(onClick = onNavigateToConfig) {
                    Text("⚙", fontSize = 18.sp, color = NeonBlue)
                }
            }

            // ── Center: Orion title + Blob + Status ──
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ORION title
                Text(
                    text = "ORION",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonBlue,
                    letterSpacing = 4.sp,
                )
                Text(
                    text = personality.displayName,
                    fontSize = 13.sp,
                    color = NeonBlue.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 40.dp),
                )

                // Blob
                BlobAnimator(
                    audioLevel = audioLevel,
                    size = 230.dp,
                    color =
                        when (voiceState) {
                            VoiceState.IDLE -> NeonBlue
                            VoiceState.LISTENING -> Color(0xFF00FF88)
                            VoiceState.PROCESSING -> Color(0xFFFFD600)
                            VoiceState.SPEAKING -> NeonBlue
                        },
                )

                Spacer(Modifier.height(40.dp))

                // Status text
                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "statusText",
                ) { text ->
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            // ── Bottom: Hold-to-Talk button ──
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
                // Outer glow ring
                Box(
                    modifier =
                        Modifier.size(90.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    showListeningButton -> Color(0xFF00FF88).copy(alpha = 0.2f)
                                    else -> NeonBlue.copy(alpha = 0.08f)
                                }
                            )
                            .border(
                                width = if (showListeningButton) 2.dp else 1.dp,
                                color =
                                    when {
                                        showListeningButton -> Color(0xFF00FF88).copy(alpha = 0.6f)
                                        else -> NeonBlue.copy(alpha = 0.3f)
                                    },
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    // Inner mic button
                    Button(
                        onClick = {
                            if (voiceState == VoiceState.PROCESSING) return@Button

                            isAutoListening = !isAutoListening

                            if (isAutoListening) {
                                voiceState = VoiceState.LISTENING
                                piperSynthesizer.stop()
                                speechToTextManager.startListening(
                                    silenceThresholdMs = (silenceThresholdSeconds * 1000).toLong(),
                                    volumeThreshold = silenceVolumeThreshold,
                                    onPartialResult = { partial ->
                                        // Barge-in check: if we hear something new while speaking,
                                        // stop TTS
                                        if (voiceState == VoiceState.SPEAKING) {
                                            piperSynthesizer.stop()
                                            voiceState = VoiceState.LISTENING
                                        }
                                        statusText = partial
                                        lastActiveTime = org.example.project.getCurrentTimeMillis()
                                    },
                                    onResult = handleSpeechResult,
                                    onAudioLevel = { level ->
                                        audioLevel = level
                                        if (level > silenceVolumeThreshold)
                                            lastActiveTime =
                                                org.example.project.getCurrentTimeMillis()
                                    },
                                    onError = { errorMsg ->
                                        if (isAutoListening) {
                                            statusText = "Escuchando..."
                                            voiceState = VoiceState.LISTENING
                                        } else {
                                            statusText = errorMsg
                                            voiceState = VoiceState.IDLE
                                        }
                                    },
                                )
                            } else {
                                // Turn off completely
                                voiceState = VoiceState.IDLE
                                statusText = "Toca el micrófono para empezar"
                                speechToTextManager.stopListening()
                                piperSynthesizer.stop()
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    when {
                                        showListeningButton -> Color(0xFF00FF88)
                                        else -> NeonBlue
                                    },
                                contentColor = DeepBlack,
                            ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    ) {
                        Text(
                            text =
                                when {
                                    showListeningButton -> "■"
                                    voiceState == VoiceState.PROCESSING -> "◌"
                                    voiceState == VoiceState.SPEAKING -> "🔊"
                                    else -> "🎤"
                                },
                            fontSize = 28.sp,
                        )
                    }
                }
            }
        }
    }
}
