package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.project.model.ChatMessage
import org.example.project.model.Personality
import org.example.project.model.toGeminiContent
import org.example.project.network.Content
import org.example.project.network.GeminiClient
import org.example.project.network.Part
import org.example.project.ui.theme.NeonBlue
import org.example.project.ui.theme.DeepBlack
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    settingsManager: org.example.project.SettingsManager,
    apiKey: String,
    userName: String,
    userGender: String,
    personality: Personality,
    voiceId: String,
    onNavigateToConfig: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    // Load history on entry: keep last 3 months (90 days), always fresh
    val ninetyDaysInMillis = 90L * 24 * 60 * 60 * 1000
    var messages by remember {
        val history = settingsManager.getChatHistory()
        val currentTime = org.example.project.getCurrentTimeMillis()
        val filtered = history.filter { currentTime - it.timestamp <= ninetyDaysInMillis }
        // Purge old messages beyond 3 months permanently
        if (filtered.size < history.size) settingsManager.saveChatHistory(filtered)
        mutableStateOf(filtered)
    }

    // Reload whenever we come back from VoiceScreen (new voice messages)
    LaunchedEffect(Unit) {
        val history = settingsManager.getChatHistory()
        val currentTime = org.example.project.getCurrentTimeMillis()
        messages = history.filter { currentTime - it.timestamp <= ninetyDaysInMillis }
    }

    // In-memory conversation history for multi-turn Gemini (last 15 messages as seed)
    val conversationHistory = remember {
        mutableStateListOf<Content>().also { list ->
            messages.takeLast(15).forEach { msg ->
                list.add(msg.toGeminiContent())
            }
        }
    }

    var wordCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateToVoice()
    }

    val coroutineScope = rememberCoroutineScope()
    val geminiClient = remember { GeminiClient(apiKey) }
    val maxWords = 10
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    FuturisticBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Header Bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                DeepBlack,
                                DeepBlack.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF141428).copy(alpha = 0.8f))
                                .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 4.dp)
                        ) {
                            TextButton(onClick = onNavigateToVoice) {
                                Text("‹", fontSize = 18.sp, color = NeonBlue)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ORION",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonBlue,
                                letterSpacing = 3.sp
                            )
                            Text(
                                text = personality.displayName,
                                fontSize = 13.sp,
                                color = NeonBlue.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141428).copy(alpha = 0.8f))
                            .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    ) {
                        TextButton(onClick = onNavigateToConfig) {
                            Text("⚙", fontSize = 18.sp, color = NeonBlue)
                        }
                    }
                }
            }

            // ── Messages List ──
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NeonBlue.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Orion está pensando...",
                                color = NeonBlue.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // ── Floating Input Area ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                DeepBlack.copy(alpha = 0.9f),
                                DeepBlack
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Word counter
                Text(
                    text = "$wordCount / $maxWords",
                    color = if (wordCount > maxWords) MaterialTheme.colorScheme.error else NeonBlue.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                )

                // Input pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1A1A2E))
                        .border(
                            width = 1.dp,
                            color = NeonBlue.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(inputText, isLoading, maxWords, wordCount,
                        onValueChange = { newText ->
                            inputText = newText
                            wordCount = newText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Circular send button
                    val canSend = inputText.isNotBlank() && wordCount <= maxWords && !isLoading
                    Button(
                        onClick = {
                            if (canSend) {
                                val userMessage = inputText
                                val newMsg = ChatMessage(isUser = true, text = userMessage, timestamp = org.example.project.getCurrentTimeMillis())
                                messages = messages + newMsg
                                settingsManager.saveChatHistory(messages)
                                onSendMessage(userMessage)

                                inputText = ""
                                wordCount = 0
                                isLoading = true

                                coroutineScope.launch {
                                    val todayStr = org.example.project.getCurrentDate()
                                    val lastGreeting = settingsManager.getLastDailyGreetingDate()
                                    val greetingRule = if (lastGreeting != todayStr) {
                                        "Es la primera vez que hablas con el usuario hoy. Salúdalo amistosamente."
                                    } else {
                                        "Ya has hablado con el usuario hoy. Continúa la conversación natural, NO saludes de nuevo."
                                    }

                                    val systemInstruction = "Eres una Inteligencia Artificial llamada Orion. El usuario se llama $userName y su género es $userGender. Hoy es $todayStr. Hablas en español de España. $greetingRule REGLA DE ORO: Tus respuestas deben ser EXTREMADAMENTE BREVES y conversacionales, como si estuvieras hablando en persona. MÁXIMO 1 o 2 oraciones para preguntas simples o saludos. Si es una duda compleja, ve directo al grano sin introducciones largas. NUNCA sueltes párrafos largos ni listas a menos que se te pida explícitamente 'explícame en detalle'. No repitas el nombre del usuario constantemente.\n${personality.promptPrefix}"

                                    val reminderRules = "REGLA ESTRICTA DE RECORDATORIOS: Si el usuario pide crear un recordatorio, responde ÚNICAMENTE con ||RECORDATORIO:AAAA-MM-DD|Texto|| usando la fecha ISO exacta. Hoy es $todayStr. Si pregunta por sus recordatorios, responde ÚNICAMENTE con [LEER_RECORDATORIOS]."
                                    val appLaunchRules = " REGLA ESTRICTA DE APERTURA DE APPS: Si el usuario pide abrir o lanzar una aplicación, responde ÚNICAMENTE con ||ABRIR_APP:Nombre de la app||. No añadas explicación."

                                    var dailyGreetingContext = ""
                                    if (lastGreeting != todayStr) {
                                        val todaysReminders = settingsManager.getReminders().filter { org.example.project.model.isReminderOnDate(it, todayStr) && !it.isCompleted }
                                        if (todaysReminders.isNotEmpty()) {
                                            dailyGreetingContext = "\nIMPORTANTE: Es la primera vez que hablas con el usuario hoy. Tiene estos recordatorios para HOY: " +
                                                todaysReminders.joinToString("; ") { it.text } + ". Menciónalos sutilmente."
                                        }
                                    }

                                    val importantFacts = settingsManager.getImportantFacts()
                                    val factsContext = if (importantFacts.isNotEmpty()) {
                                        "\nDatos importantes sobre el usuario:\n" + importantFacts.mapIndexed { i, f -> "${i+1}. $f" }.joinToString("\n")
                                    } else ""

                                    val fullSystemInstruction = systemInstruction + reminderRules + appLaunchRules + factsContext + dailyGreetingContext

                                    val responseText = geminiClient.generateWithHistory(
                                        systemInstruction = fullSystemInstruction,
                                        history = conversationHistory.toList(),
                                        userMessage = userMessage
                                    ).trim()
                                    val finalResponse: String
                                    if (responseText.startsWith("||ABRIR_APP:") && responseText.endsWith("||")) {
                                        val appQuery = responseText.removePrefix("||ABRIR_APP:").removeSuffix("||").trim()
                                        val launchedApp = org.example.project.openInstalledApp(appQuery)
                                        val appNotFoundResponses = listOf(
                                            "No encuentro una app llamada $appQuery.",
                                            "No veo ninguna app parecida a $appQuery.",
                                            "No he encontrado esa app en el móvil.",
                                            "No me aparece ninguna aplicación con ese nombre.",
                                            "No encuentro $appQuery entre tus apps instaladas.",
                                            "No he podido localizar esa app.",
                                            "No veo una coincidencia clara para $appQuery.",
                                            "No parece que tengas una app llamada así.",
                                            "No he encontrado nada suficientemente parecido a $appQuery.",
                                            "No consigo abrirla porque no encuentro esa aplicación."
                                        )
                                        finalResponse = if (launchedApp != null) "Abriendo $launchedApp." else appNotFoundResponses.random()
                                    } else if (responseText.startsWith("||RECORDATORIO:") && responseText.endsWith("||")) {
                                        val content = responseText.removePrefix("||RECORDATORIO:").removeSuffix("||")
                                        val parts = content.split("|", limit = 2)
                                        if (parts.size == 2) {
                                            val date = org.example.project.model.normalizeReminderDate(parts[0])
                                            val text = parts[1]
                                            val newReminder = org.example.project.model.Reminder(
                                                id = org.example.project.getCurrentTimeMillis().toString(),
                                                date = date,
                                                text = text
                                            )
                                            settingsManager.saveReminders(settingsManager.getReminders() + newReminder)
                                            
                                            val confirmPrompt = "$fullSystemInstruction\nEl usuario pidió guardar el recordatorio '$text' para el día '$date' y el sistema ya lo ha guardado correctamente. Confírmale al usuario brevemente que ya está guardado."
                                            finalResponse = geminiClient.generateContent(confirmPrompt).trim()
                                        } else {
                                            finalResponse = "Error al procesar el recordatorio."
                                        }
                                    } else if (responseText.startsWith("[LEER_RECORDATORIOS]")) {
                                        val pending = settingsManager.getReminders().filter { !it.isCompleted }
                                        val remindersText = if (pending.isEmpty()) "No hay recordatorios pendientes." else pending.joinToString(", ") { "Día ${org.example.project.model.formatReminderDateForUser(it.date)}: ${it.text}" }
                                        
                                        val readPrompt = "$fullSystemInstruction\nEl usuario quiere saber sus recordatorios. La base de datos dice: $remindersText. Díselo al usuario de forma natural."
                                        finalResponse = geminiClient.generateContent(readPrompt).trim()
                                    } else {
                                        finalResponse = responseText
                                    }

                                    if (lastGreeting != todayStr) settingsManager.saveLastDailyGreetingDate(todayStr)

                                    val responseMsg = ChatMessage(isUser = false, text = finalResponse, timestamp = org.example.project.getCurrentTimeMillis())
                                    messages = messages + responseMsg
                                    settingsManager.saveChatHistory(messages)

                                    // Update in-memory conversation history for next turn
                                    conversationHistory.add(Content(role = "user", parts = listOf(Part(userMessage))))
                                    conversationHistory.add(Content(role = "model", parts = listOf(Part(finalResponse))))
                                    // Keep sliding window of last 30 turns (15 exchanges)
                                    while (conversationHistory.size > 30) conversationHistory.removeAt(0)

                                    isLoading = false
                                }
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonBlue,
                            contentColor = DeepBlack,
                            disabledContainerColor = NeonBlue.copy(alpha = 0.2f),
                            disabledContentColor = DeepBlack.copy(alpha = 0.3f)
                        )
                    ) {
                        Text("➤", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BasicTextField(
    inputText: String,
    isLoading: Boolean,
    maxWords: Int,
    wordCount: Int,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = inputText,
        onValueChange = onValueChange,
        modifier = Modifier.weight(1f),
        placeholder = { Text("Escribe un mensaje...", color = Color(0xFF555577)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = NeonBlue,
            focusedTextColor = Color(0xFFE0E0E0),
            unfocusedTextColor = Color(0xFFE0E0E0)
        ),
        enabled = !isLoading,
        singleLine = true
    )
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (message.isUser) {
            // User bubble: solid NeonBlue, right-aligned
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(NeonBlue)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = DeepBlack,
                    fontSize = 15.sp
                )
            }
        } else {
            // AI bubble: glass/holographic with subtle glow border
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .background(Color(0xFF141428).copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        color = NeonBlue.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = Color(0xFFE0E0E0),
                    fontSize = 15.sp
                )
            }
        }
    }
}

