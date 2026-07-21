

- [x] 1. Permisos y Tutorial:
  - [x] Añadir `SYSTEM_ALERT_WINDOW` a `AndroidManifest.xml`.
  - [x] Solicitar permiso de superposición en `TutorialPersonalityScreen.kt` o `MainActivity.kt`.
  - [x] Añadir botón en Configuración para activar superposición si no se hizo.
- [x] 2. Ajustes de Asistente (`SettingsManager.kt` y `ConfigurationScreen.kt`):
  - [x] Añadir `getAssistantSilenceTimeout(): Int` y `saveAssistantSilenceTimeout(seconds: Int)`.
  - [x] Añadir sección "Ajustes Modo Asistente" en la UI.
- [x] 3. Mejoras de Audio (`OrionHotwordService.kt` y STT):
  - [x] Añadir `AcousticEchoCanceler` al `AudioRecord` para evitar escuchar la propia música del teléfono.
  - [x] Añadir `NoiseSuppressor` para mayor claridad de voz.
- [x] 4. Burbuja Flotante (`FloatingBubbleManager.kt` / `OrionHotwordService.kt`):
  - [x] Crear `FloatingBubbleManager.kt` con `WindowManager` + `ComposeView`.
  - [x] Renderizar burbuja pulsante animada (azul=espera, verde=voz detectada).
  - [x] Lógica de arrastre con `pointerInput` + botón X para cerrar/detener servicio.
  - [x] Integrar en `OrionHotwordService.onCreate()` y `onDestroy()`.
  - [x] Actualizar `isMicActive` en tiempo real con RMS del audio.
- [x] 5. Temporizador de Silencio (VoiceScreen.kt):
  - [x] `LaunchedEffect` que rastrea `lastActiveTime` y suspende tras X segundos de silencio.
  - [x] Configuración del timeout en ajustes (5–60 s).
  - [x] Mensaje de estado "Modo suspensión" al activarse.

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.audio.ModelDownloader
import org.example.project.SettingsManager
import org.example.project.ui.theme.DeepBlack
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.NeonBlue
import org.example.project.BackHandler
import org.example.project.openSystemSettings
import org.example.project.openBatterySettings

private fun String.wordCount() = this.trim().split("\\s+".toRegex()).count { it.isNotBlank() }

private enum class ConfigPage {
    MENU, USER_DATA, MEMORY, SILENCE, VOICE, SYSTEM, DOWNLOAD_MODELS, EVENTS
}

@Composable
fun ConfigurationScreen(
    settingsManager: SettingsManager,
    modelDownloader: ModelDownloader,
    userName: String,
    onBack: (newName: String?) -> Unit
) {
    var currentPage by remember { mutableStateOf(ConfigPage.MENU) }
    var currentName by remember { mutableStateOf(userName) }
    var nameEdited by remember { mutableStateOf(false) }

    BackHandler {
        if (currentPage == ConfigPage.MENU) {
            onBack(if (nameEdited) currentName else null)
        } else {
            currentPage = ConfigPage.MENU
        }
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState == ConfigPage.MENU) {
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
            } else {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            }
        },
        label = "configNav"
    ) { page ->
        when (page) {
            ConfigPage.MENU -> ConfigMenuScreen(
                onNavigate = { currentPage = it },
                onBack = { onBack(if (nameEdited) currentName else null) }
            )
            ConfigPage.USER_DATA -> UserDataSubScreen(
                settingsManager = settingsManager,
                currentName = currentName,
                onNameChanged = { newName -> currentName = newName; nameEdited = true },
                onBack = { currentPage = ConfigPage.MENU }
            )
            ConfigPage.MEMORY -> MemorySubScreen(
                settingsManager = settingsManager,
                onBack = { currentPage = ConfigPage.MENU }
            )
            ConfigPage.SILENCE -> SilenceSubScreen(onBack = { currentPage = ConfigPage.MENU })
            ConfigPage.VOICE -> VoiceSubScreen(
                settingsManager = settingsManager,
                modelDownloader = modelDownloader,
                onNavigateToDownload = { currentPage = ConfigPage.DOWNLOAD_MODELS },
                onBack = { currentPage = ConfigPage.MENU }
            )
            ConfigPage.SYSTEM -> SystemSubScreen(onBack = { currentPage = ConfigPage.MENU })
            ConfigPage.DOWNLOAD_MODELS -> TutorialModelDownloadScreen(
                modelDownloader = modelDownloader,
                onDownloadComplete = { currentPage = ConfigPage.VOICE }
            )
            ConfigPage.EVENTS -> EventsSubScreen(
                settingsManager = settingsManager,
                onBack = { currentPage = ConfigPage.MENU }
            )
        }
    }
}

@Composable
private fun ConfigMenuScreen(onNavigate: (ConfigPage) -> Unit, onBack: () -> Unit) {
    FuturisticBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 120.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("CONFIGURACI\u00d3N", fontSize = 26.sp, fontWeight = FontWeight.Black, color = NeonBlue, letterSpacing = 3.sp)
                    Text("Personaliza c\u00f3mo Orion te conoce", fontSize = 14.sp, color = NeonBlue.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp, bottom = 32.dp))
                }
                item { ConfigCard(icon = "\ud83d\udc64", title = "Datos del Usuario", subtitle = "Nombre y perfil b\u00e1sico", onClick = { onNavigate(ConfigPage.USER_DATA) }) }
                item { ConfigCard(icon = "\ud83e\udde0", title = "Memoria de Orion", subtitle = "Datos que siempre recuerda de ti", onClick = { onNavigate(ConfigPage.MEMORY) }) }
                item { ConfigCard(icon = "\ud83c\udfa4", title = "Detecci\u00f3n de Silencio", subtitle = "Cu\u00e1ndo enviar tu mensaje autom\u00e1ticamente", onClick = { onNavigate(ConfigPage.SILENCE) }) }
                item { ConfigCard(icon = "\ud83d\udde3\ufe0f", title = "Voz de la IA", subtitle = "Cambia la voz con la que te habla Orion", highlight = true, onClick = { onNavigate(ConfigPage.VOICE) }) }
                item { ConfigCard(icon = "\u2699\ufe0f", title = "Integraci\u00f3n y Sistema", subtitle = "Ajustes de asistente y permisos de Android", onClick = { onNavigate(ConfigPage.SYSTEM) }) }
                item { ConfigCard(icon = "\ud83d\udce1", title = "Eventos de Activaci\u00f3n", subtitle = "Activa a Orion al conectar al coche, WiFi y m\u00e1s", onClick = { onNavigate(ConfigPage.EVENTS) }) }
            }
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 20.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFF141428).copy(alpha = 0.8f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).clickable(onClick = onBack)
            ) {
                Text("\u2039 Volver", color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ConfigCard(icon: String, title: String, subtitle: String, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (highlight) NeonBlue.copy(alpha = 0.08f) else Color(0xFF0D0D1A))
            .border(1.dp, if (highlight) NeonBlue.copy(alpha = 0.6f) else NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(NeonBlue.copy(alpha = 0.12f)).border(1.dp, NeonBlue.copy(alpha = 0.2f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 22.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (highlight) NeonBlue else Color(0xFFE0E0E0))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF707080), modifier = Modifier.padding(top = 2.dp))
            }
            Text("\u203a", fontSize = 22.sp, color = NeonBlue.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SubScreenHeader(icon: String, title: String, subtitle: String, onBack: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF141428).copy(alpha = 0.8f)).border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).clickable(onClick = onBack)) {
        Text("\u2039 Ajustes", color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = NeonBlue, letterSpacing = 1.sp)
            Text(subtitle, fontSize = 13.sp, color = NeonBlue.copy(alpha = 0.5f))
        }
    }
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = NeonBlue.copy(alpha = 0.15f))
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun UserDataSubScreen(settingsManager: SettingsManager, currentName: String, onNameChanged: (String) -> Unit, onBack: () -> Unit) {
    var nameInput by remember { mutableStateOf(currentName) }
    var saved by remember { mutableStateOf(false) }
    fun saveName() {
        val trimmed = nameInput.trim()
        if (trimmed.isNotBlank()) { settingsManager.saveUserDetails(trimmed, settingsManager.getUserGender() ?: ""); onNameChanged(trimmed); saved = true }
    }
    FuturisticBackground {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp)) {
            item {
                SubScreenHeader(icon = "\ud83d\udc64", title = "Datos del Usuario", subtitle = "Tu perfil e identidad", onBack = onBack)
                Text("NOMBRE DE USUARIO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = NeonBlue.copy(alpha = 0.7f), letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it; saved = false }, modifier = Modifier.weight(1f), placeholder = { Text("Tu nombre o apodo", color = Color(0xFF555577)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { saveName() }), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonBlue, unfocusedBorderColor = NeonBlue.copy(alpha = 0.25f), focusedTextColor = Color(0xFFE0E0E0), unfocusedTextColor = Color(0xFFE0E0E0), cursorColor = NeonBlue, focusedContainerColor = Color(0xFF0D0D1A), unfocusedContainerColor = Color(0xFF0D0D1A)), shape = RoundedCornerShape(14.dp))
                    Button(onClick = ::saveName, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack), modifier = Modifier.height(56.dp)) { Text("Guardar", fontWeight = FontWeight.Bold) }
                }
                if (saved) Text("\u2713 Guardado como \"${nameInput.trim()}\"", color = Color(0xFF00FF88), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Spacer(Modifier.height(32.dp))
                InfoBox("Este nombre es el que Orion usar\u00e1 para dirigirse a ti en la conversaci\u00f3n.")
            }
        }
    }
}

@Composable
private fun MemorySubScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    var importantFacts by remember { mutableStateOf(settingsManager.getImportantFacts()) }
    var newFactInput by remember { mutableStateOf("") }
    var newFactError by remember { mutableStateOf<String?>(null) }
    val maxFacts = 20; val maxWordsPerFact = 20
    fun addFact() {
        val trimmed = newFactInput.trim(); val wordCount = trimmed.wordCount()
        when {
            trimmed.isBlank() -> newFactError = "Escribe un dato antes de a\u00f1adir."
            wordCount < 1 -> newFactError = "El dato debe tener al menos 1 palabra."
            wordCount > maxWordsPerFact -> newFactError = "M\u00e1ximo $maxWordsPerFact palabras ($wordCount escritas)."
            importantFacts.size >= maxFacts -> newFactError = "L\u00edmite de $maxFacts datos alcanzado."
            else -> { val updated = importantFacts + trimmed; importantFacts = updated; settingsManager.saveImportantFacts(updated); newFactInput = ""; newFactError = null }
        }
    }
    fun removeFact(index: Int) { val updated = importantFacts.toMutableList().also { it.removeAt(index) }; importantFacts = updated; settingsManager.saveImportantFacts(updated) }
    FuturisticBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SubScreenHeader(icon = "\ud83e\udde0", title = "Memoria de Orion", subtitle = "Datos que siempre recordar\u00e1 de ti", onBack = onBack)
                    InfoBox("Son hechos que Orion incluir\u00e1 siempre en sus respuestas. \u00dasalos para compartir preferencias, restricciones o contexto permanente.\n\nEjemplos: \"Soy vegetariano\", \"Vivo en Madrid\", \"Trabajo como m\u00e9dico\".")
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Datos guardados", fontSize = 13.sp, color = Color(0xFF808090))
                        Text("${importantFacts.size} / $maxFacts", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (importantFacts.size >= maxFacts) Color(0xFFFF5555) else NeonBlue)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                itemsIndexed(importantFacts) { index, fact -> FactItem(index = index + 1, text = fact, onDelete = { removeFact(index) }) }
                item {
                    Spacer(Modifier.height(8.dp))
                    if (importantFacts.size < maxFacts) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(value = newFactInput, onValueChange = { newFactInput = it; newFactError = null }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Escribe un dato (1-20 palabras)...", color = Color(0xFF555577)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { addFact() }), isError = newFactError != null, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonBlue, unfocusedBorderColor = NeonBlue.copy(alpha = 0.25f), errorBorderColor = Color(0xFFFF5555), focusedTextColor = Color(0xFFE0E0E0), unfocusedTextColor = Color(0xFFE0E0E0), cursorColor = NeonBlue, focusedContainerColor = Color(0xFF0D0D1A), unfocusedContainerColor = Color(0xFF0D0D1A)), shape = RoundedCornerShape(14.dp), trailingIcon = { val words = newFactInput.trim().wordCount(); Text("$words/$maxWordsPerFact", fontSize = 11.sp, color = if (words > maxWordsPerFact) Color(0xFFFF5555) else Color(0xFF505060), modifier = Modifier.padding(end = 8.dp)) })
                                if (newFactError != null) Text(newFactError!!, color = Color(0xFFFF5555), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                            }
                            Button(onClick = ::addFact, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp), colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)) { Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A0A0A)).border(1.dp, Color(0xFFFF5555).copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("L\u00edmite de $maxFacts datos alcanzado. Elimina alguno para a\u00f1adir m\u00e1s.", fontSize = 12.sp, color = Color(0xFFFF5555), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

    }
}

@Composable
private fun SystemSubScreen(onBack: () -> Unit) {
    var hasAssistant by remember { mutableStateOf(false) }
    var hasBattery by remember { mutableStateOf(false) }
    var hasAppPerms by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            hasAssistant = org.example.project.isAssistantRoleGranted()
            hasBattery = org.example.project.isBatteryOptimizationIgnored()
            hasAppPerms = org.example.project.hasPermission("android.permission.RECORD_AUDIO")
            kotlinx.coroutines.delay(1000)
        }
    }
    FuturisticBackground {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { SubScreenHeader(icon = "\u2699\ufe0f", title = "Integraci\u00f3n y Sistema", subtitle = "Permisos y configuraci\u00f3n de Android", onBack = onBack) }
            item { TutorialCard(icon = "\ud83e\udd16", title = "Asistente Predeterminado", accentColor = NeonBlue, isGranted = hasAssistant, steps = listOf("Abre los Ajustes de tu m\u00f3vil Android.", "Ve a \"Aplicaciones\" \u2192 \"Aplicaciones predeterminadas\".", "Toca la opci\u00f3n \"Aplicaci\u00f3n de asistencia digital\" (o \"Asistente de voz\").", "Selecciona \"Orion\" en la lista.", "\u00a1Listo! Ahora mant\u00e9n pulsado el bot\u00f3n de inicio para invocar a Orion desde cualquier app."), buttonText = "Abrir Ajustes de Apps", onButtonClick = { org.example.project.openSystemSettings() }) }
            item { TutorialCard(icon = "\ud83d\udd0b", title = "Sin Restricciones de Bater\u00eda", accentColor = Color(0xFFFFD600), isGranted = hasBattery, steps = listOf("Abre los Ajustes de tu m\u00f3vil Android.", "Ve a \"Bater\u00eda\" \u2192 \"Optimizaci\u00f3n de bater\u00eda\" (o \"Ahorro de bater\u00eda\").", "Busca la app \"Orion\" en la lista.", "Selecciona \"No optimizar\" o \"Sin restricciones\".", "Esto evita que Android limite o duerma a Orion cuando est\u00e1 en segundo plano."), buttonText = "Abrir Ajustes de Bater\u00eda", onButtonClick = { org.example.project.openBatterySettings() }) }
            item { TutorialCard(icon = "\ud83c\udfa4", title = "Permiso de Micr\u00f3fono", accentColor = Color(0xFF00FF88), isGranted = hasAppPerms, steps = listOf("Si Orion no te escucha, puede que le hayas denegado el micr\u00f3fono.", "Ve a Ajustes \u2192 Aplicaciones \u2192 Orion \u2192 Permisos.", "Activa el permiso de \"Micr\u00f3fono\".", "Recomendamos elegir \"Permitir solo mientras se usa la app\"."), buttonText = "Abrir Ajustes de la App", onButtonClick = { org.example.project.openSystemSettings() }) }
            item { TutorialCard(icon = "\ud83d\udce1", title = "Permisos para Eventos (Bluetooth/WiFi)", accentColor = Color(0xFFFF5555), isGranted = hasAppPerms, steps = listOf("Para que Orion se active al conectar auriculares o entrar al coche, necesita permisos adicionales.", "Ve a Ajustes \u2192 Aplicaciones \u2192 Orion \u2192 Permisos.", "Concede los permisos de \"Dispositivos cercanos\" (para Bluetooth) y/o \"Ubicaci\u00f3n\" (para detectar redes WiFi).", "Sin esto, los eventos personalizados podr\u00edan fallar silenciosamente."), buttonText = "Abrir Ajustes de la App", onButtonClick = { org.example.project.openSystemSettings() }) }
        }
@Composable
private fun SilenceSubScreen(onBack: () -> Unit) {
    var silenceSeconds by remember { mutableStateOf(2f) }
    var volumeThreshold by remember { mutableStateOf(0.10f) }
    FuturisticBackground {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp)) {
            item {
                SubScreenHeader(icon = "\ud83c\udfa4", title = "Detecci\u00f3n de Silencio", subtitle = "Cu\u00e1ndo Orion deja de escucharte", onBack = onBack)
                InfoBox("Si el volumen cae por debajo del umbral durante m\u00e1s de ${String.format("%.1f", silenceSeconds)}s, tu mensaje se env\u00eda autom\u00e1ticamente.")
                Spacer(Modifier.height(24.dp))
                SliderRow(label = "Tiempo de silencio", value = silenceSeconds, valueLabel = String.format("%.1f s", silenceSeconds), range = 0.5f..5f, steps = 8, onValueChange = { silenceSeconds = it })
                Spacer(Modifier.height(20.dp))
                SliderRow(label = "Umbral de volumen", value = volumeThreshold, valueLabel = "${(volumeThreshold * 100).toInt()}%", range = 0.02f..0.30f, steps = 0, onValueChange = { volumeThreshold = it })
            }
        }
    }
}

@Composable
private fun VoiceSubScreen(settingsManager: SettingsManager, modelDownloader: ModelDownloader, onNavigateToDownload: () -> Unit, onBack: () -> Unit) {
    val currentVoiceId = settingsManager.getVoiceId()
    var selectedVoice by remember { mutableStateOf(AVAILABLE_VOICES.find { it.id == currentVoiceId } ?: AVAILABLE_VOICES.first()) }
    var saved by remember { mutableStateOf(false) }
    val downloadableVoices = AVAILABLE_VOICES.filter { it.id != "android_native" }
    val missingCount = downloadableVoices.count { !modelDownloader.isModelDownloaded(it.id) }
    val isHotwordReady = modelDownloader.isHotwordModelDownloaded()
    FuturisticBackground {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SubScreenHeader(icon = "\ud83d\udde3\ufe0f", title = "Voz de la IA", subtitle = "Elige c\u00f3mo suena Orion", onBack = onBack) }
            items(AVAILABLE_VOICES) { voice ->
                val isSelected = selectedVoice == voice
                val isDownloaded = voice.id == "android_native" || modelDownloader.isModelDownloaded(voice.id)
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (isSelected) NeonBlue.copy(alpha = 0.12f) else Color(0xFF0D0D1A)).border(width = if (isSelected) 2.dp else 1.dp, color = if (isSelected) NeonBlue else NeonBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(18.dp)).clickable(enabled = isDownloaded) { selectedVoice = voice; saved = false }.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (isDownloaded) 1f else 0.5f)) {
                        Text(voice.emoji, fontSize = 30.sp, modifier = Modifier.padding(end = 14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(voice.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) NeonBlue else Color(0xFFE0E0E0))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isDownloaded) Color(0xFF00FF88).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                    Text(if (isDownloaded) "\u2713 Instalada" else "\u21af Falta descargar", fontSize = 10.sp, color = if (isDownloaded) Color(0xFF00FF88) else Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("${voice.gender} \u00b7 ${voice.description}", fontSize = 12.sp, color = Color(0xFF707080), modifier = Modifier.padding(top = 3.dp))
                        }
                        if (isSelected) Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(NeonBlue), contentAlignment = Alignment.Center) { Text("\u2713", fontSize = 13.sp, color = DeepBlack, fontWeight = FontWeight.Black) }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                if (missingCount > 0 || !isHotwordReady) {
                    Button(onClick = onNavigateToDownload, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f), contentColor = Color(0xFFFF9800))) {
                        Text(when { missingCount > 0 && !isHotwordReady -> "Descargar voces y activaci\u00f3n Orion"; missingCount > 0 -> "Descargar voces faltantes ($missingCount)"; else -> "Descargar activaci\u00f3n por Orion" }, fontWeight = FontWeight.Bold)
                    }
                    if (!isHotwordReady) Text("Necesario para que los eventos escuchen la palabra Orion.", fontSize = 12.sp, color = Color(0xFFFF9800).copy(alpha = 0.85f), modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF00FF88).copy(alpha = 0.06f)).border(1.dp, Color(0xFF00FF88).copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("\u2713 Voces y activaci\u00f3n por Orion instaladas", fontSize = 13.sp, color = Color(0xFF00FF88))
                    }
                }
            }
            item {
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tu nombre o apodo", color = Color(0xFF555577)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveName() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = NeonBlue.copy(alpha = 0.25f),
                            focusedTextColor = Color(0xFFE0E0E0),
                            unfocusedTextColor = Color(0xFFE0E0E0),
                            cursorColor = NeonBlue,
                            focusedContainerColor = Color(0xFF0D0D1A),
                            unfocusedContainerColor = Color(0xFF0D0D1A)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = ::saveName,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonBlue, contentColor = DeepBlack
                        ),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }

                if (saved) {
                                cursorColor = NeonBlue,
                                focusedContainerColor = Color(0xFF0D0D1A),
                                unfocusedContainerColor = Color(0xFF0D0D1A),
                            ),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Button(
                        onClick = ::saveName,
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonBlue,
                                contentColor = DeepBlack,
                            ),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
                if (saved)
                    Text(
                        "\u2713 Guardado como \"${nameInput.trim()}\"",
                        color = Color(0xFF00FF88),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                Spacer(Modifier.height(32.dp))
                InfoBox(
                    "Este nombre es el que Orion usar\u00e1 para dirigirse a ti en la conversaci\u00f3n."
                )
            }
        }
    }
}

@Composable
private fun MemorySubScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    var importantFacts by remember { mutableStateOf(settingsManager.getImportantFacts()) }
    var newFactInput by remember { mutableStateOf("") }
    var newFactError by remember { mutableStateOf<String?>(null) }
    val maxFacts = 20
    val maxWordsPerFact = 20
    fun addFact() {
        val trimmed = newFactInput.trim()
        val wordCount = trimmed.wordCount()
        when {
            trimmed.isBlank() -> newFactError = "Escribe un dato antes de a\u00f1adir."
            wordCount < 1 -> newFactError = "El dato debe tener al menos 1 palabra."
            wordCount > maxWordsPerFact ->
                newFactError = "M\u00e1ximo $maxWordsPerFact palabras ($wordCount escritas)."
            importantFacts.size >= maxFacts ->
                newFactError = "L\u00edmite de $maxFacts datos alcanzado."
            else -> {
                val updated = importantFacts + trimmed
                importantFacts = updated
                settingsManager.saveImportantFacts(updated)
                newFactInput = ""
                newFactError = null
            }
        }
    }
    fun removeFact(index: Int) {
        val updated = importantFacts.toMutableList().also { it.removeAt(index) }
        importantFacts = updated
        settingsManager.saveImportantFacts(updated)
    }
    FuturisticBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SubScreenHeader(
                        icon = "\ud83e\udde0",
                        title = "Memoria de Orion",
                        subtitle = "Datos que siempre recordar\u00e1 de ti",
                        onBack = onBack,
                    )
                    InfoBox(
                        "Son hechos que Orion incluir\u00e1 siempre en sus respuestas. \u00dasalos para compartir preferencias, restricciones o contexto permanente.\n\nEjemplos: \"Soy vegetariano\", \"Vivo en Madrid\", \"Trabajo como m\u00e9dico\"."
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Datos guardados", fontSize = 13.sp, color = Color(0xFF808090))
                        Text(
                            "${importantFacts.size} / $maxFacts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (importantFacts.size >= maxFacts) Color(0xFFFF5555) else NeonBlue,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                itemsIndexed(importantFacts) { index, fact ->
                    FactItem(index = index + 1, text = fact, onDelete = { removeFact(index) })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    if (importantFacts.size < maxFacts) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = newFactInput,
                                    onValueChange = {
                                        newFactInput = it
                                        newFactError = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            "Escribe un dato (1-20 palabras)...",
                                            color = Color(0xFF555577),
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addFact() }),
                                    isError = newFactError != null,
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonBlue,
                                            unfocusedBorderColor = NeonBlue.copy(alpha = 0.25f),
                                            errorBorderColor = Color(0xFFFF5555),
                                            focusedTextColor = Color(0xFFE0E0E0),
                                            unfocusedTextColor = Color(0xFFE0E0E0),
                                            cursorColor = NeonBlue,
                                            focusedContainerColor = Color(0xFF0D0D1A),
                                            unfocusedContainerColor = Color(0xFF0D0D1A),
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    trailingIcon = {
                                        val words = newFactInput.trim().wordCount()
                                        Text(
                                            "$words/$maxWordsPerFact",
                                            fontSize = 11.sp,
                                            color =
                                                if (words > maxWordsPerFact) Color(0xFFFF5555)
                                                else Color(0xFF505060),
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    },
                                )
                                if (newFactError != null)
                                    Text(
                                        newFactError!!,
                                        color = Color(0xFFFF5555),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                    )
                            }
                            Button(
                                onClick = ::addFact,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(52.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = NeonBlue,
                                        contentColor = DeepBlack,
                                    ),
                            ) {
                                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1A0A0A))
                                    .border(
                                        1.dp,
                                        Color(0xFFFF5555).copy(alpha = 0.4f),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .padding(12.dp),
                            contentAlignment = Alignment.Center,
                    onValueChange = { silenceSeconds = it }
                )
                Spacer(Modifier.height(20.dp))
                SliderRow(
                    label = "Umbral de volumen",
                    value = volumeThreshold,
                    valueLabel = "${(volumeThreshold * 100).toInt()}%",
                    range = 0.02f..0.30f, steps = 0,
                    onValueChange = { volumeThreshold = it }
                )
            }
        }
                    accentColor = NeonBlue,
                    isGranted = hasAssistant,
                    steps = listOf(
                        "Abre los Ajustes de tu móvil Android.",
                        "Ve a "Aplicaciones" → "Aplicaciones predeterminadas".",
                        "Toca la opción "Aplicación de asistencia digital" (o "Asistente de voz").",
                        "Selecciona "Orion" en la lista.",
                        "¡Listo! Ahora mantén pulsado el botón de inicio para invocar a Orion desde cualquier app."
                    ),
                    buttonText = "Abrir Ajustes de Apps",
                    onButtonClick = { org.example.project.openSystemSettings() }
                )
            }
            item {
                TutorialCard(
                    icon = "🔋",
                    title = "Sin Restricciones de Batería",
                    accentColor = Color(0xFFFFD600),
                    isGranted = hasBattery,
                    steps = listOf(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SubScreenHeader(
                    icon = "\u2699\ufe0f",
                    title = "Integraci\u00f3n y Sistema",
                    subtitle = "Permisos y configuraci\u00f3n de Android",
                    onBack = onBack,
                )
            }
            item {
                TutorialCard(
                    icon = "\ud83e\udd16",
                    title = "Asistente Predeterminado",
                    accentColor = NeonBlue,
                    isGranted = hasAssistant,
                    steps =
                        listOf(
                            "Abre los Ajustes de tu m\u00f3vil Android.",
                            "Ve a \"Aplicaciones\" \u2192 \"Aplicaciones predeterminadas\".",
                            "Toca la opci\u00f3n \"Aplicaci\u00f3n de asistencia digital\" (o \"Asistente de voz\").",
                            "Selecciona \"Orion\" en la lista.",
                            "\u00a1Listo! Ahora mant\u00e9n pulsado el bot\u00f3n de inicio para invocar a Orion desde cualquier app.",
                        ),
                    buttonText = "Abrir Ajustes de Apps",
                    onButtonClick = { org.example.project.openSystemSettings() },
                )
            }
            item {
                TutorialCard(
                    icon = "\ud83d\udd0b",
                    title = "Sin Restricciones de Bater\u00eda",
                    accentColor = Color(0xFFFFD600),
                    isGranted = hasBattery,
                    steps =
                        listOf(
                            "Abre los Ajustes de tu m\u00f3vil Android.",
                            "Ve a \"Bater\u00eda\" \u2192 \"Optimizaci\u00f3n de bater\u00eda\" (o \"Ahorro de bater\u00eda\").",
                            "Busca la app \"Orion\" en la lista.",
                            "Selecciona \"No optimizar\" o \"Sin restricciones\".",
                            "Esto evita que Android limite o duerma a Orion cuando est\u00e1 en segundo plano.",
                        ),
                    buttonText = "Abrir Ajustes de Bater\u00eda",
                    onButtonClick = { org.example.project.openBatterySettings() },
                )
            }
            item {
                TutorialCard(
                    icon = "\ud83c\udfa4",
                    title = "Permiso de Micr\u00f3fono",
                    accentColor = Color(0xFF00FF88),
                    isGranted = hasAppPerms,
                    steps =
                        listOf(
                            "Si Orion no te escucha, puede que le hayas denegado el micr\u00f3fono.",
                            "Ve a Ajustes \u2192 Aplicaciones \u2192 Orion \u2192 Permisos.",
                            "Activa el permiso de \"Micr\u00f3fono\".",
                            "Recomendamos elegir \"Permitir solo mientras se usa la app\".",
                        ),
                    buttonText = "Abrir Ajustes de la App",
                    onButtonClick = { org.example.project.openSystemSettings() },
                )
            }
            item {
                TutorialCard(
                    icon = "\ud83d\udce1",
                    title = "Permisos para Eventos (Bluetooth/WiFi)",
                    accentColor = Color(0xFFFF5555),
                    isGranted = hasAppPerms,
                    steps =
                        listOf(
                            "Para que Orion se active al conectar auriculares o entrar al coche, necesita permisos adicionales.",
                            "Ve a Ajustes \u2192 Aplicaciones \u2192 Orion \u2192 Permisos.",
                            "Concede los permisos de \"Dispositivos cercanos\" (para Bluetooth) y/o \"Ubicaci\u00f3n\" (para detectar redes WiFi).",
                            "Sin esto, los eventos personalizados podr\u00edan fallar silenciosamente.",
                        ),
                    buttonText = "Abrir Ajustes de la App",
                    onButtonClick = { org.example.project.openSystemSettings() },
                )
            }
            item {
                TutorialCard(
                    icon = "🫧",
                    title = "Burbuja Flotante",
                    accentColor = Color(0xFF00C3FF),
                    isGranted = hasOverlay,
                    steps =
                        listOf(
                            "Para mostrar una pequeña burbuja indicando cuándo Orion te escucha en segundo plano.",
                            "Toca el botón de abajo y activa 'Mostrar sobre otras aplicaciones' para Orion.",
                        ),
                    buttonText = "Configurar Superposición",
                    onButtonClick = { org.example.project.openOverlaySettings() },
                )
            }
        }
    }
}

@Composable
private fun SilenceSubScreen(settingsManager: SettingsManager, onBack: () -> Unit) {
    var silenceSeconds by remember { mutableStateOf(2f) }
    var volumeThreshold by remember { mutableStateOf(0.10f) }
    var assistantSilenceTimeout by remember {
        mutableStateOf(settingsManager.getAssistantSilenceTimeout().toFloat())
    }

    FuturisticBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
        ) {
            item {
                SubScreenHeader(
                    icon = "\ud83c\udfa4",
                    title = "Detecci\u00f3n de Silencio",
                    subtitle = "Cu\u00e1ndo Orion deja de escucharte",
                    onBack = onBack,
                )
                InfoBox(
                    "Si el volumen cae por debajo del umbral durante m\u00e1s de ${String.format("%.1f", silenceSeconds)}s, tu mensaje se env\u00eda autom\u00e1ticamente."
                )
                Spacer(Modifier.height(24.dp))
                SliderRow(
                    label = "Tiempo de silencio",
                    value = silenceSeconds,
                    valueLabel = String.format("%.1f s", silenceSeconds),
                    range = 0.5f..5f,
                    steps = 8,
                    onValueChange = { silenceSeconds = it },
                )
                Spacer(Modifier.height(20.dp))
                SliderRow(
                    label = "Umbral de volumen",
                    value = volumeThreshold,
                    valueLabel = "${(volumeThreshold * 100).toInt()}%",
                    range = 0.02f..0.30f,
                    steps = 0,
                    onValueChange = { volumeThreshold = it },
                )

                Spacer(Modifier.height(40.dp))
                SubScreenHeader(
                    icon = "⏱️",
                    title = "Modo Asistente",
                    subtitle = "Suspensión automática por inactividad",
                    onBack = {},
                )
                InfoBox(
                    "Si Orion está activo y no dices nada durante ${assistantSilenceTimeout.toInt()}s, se pondrá en modo suspensión y tendrás que volver a decir 'Orion' para despertarlo."
                )
                Spacer(Modifier.height(24.dp))
                SliderRow(
                    label = "Tiempo de suspensión",
                    value = assistantSilenceTimeout,
                    valueLabel = "${assistantSilenceTimeout.toInt()} s",
                    range = 5f..60f,
                    steps = 10,
                    onValueChange = {
                        assistantSilenceTimeout = it
                        settingsManager.setAssistantSilenceTimeout(it.toInt())
                    },
                )
            }
        }
    }
}

@Composable
private fun VoiceSubScreen(
    settingsManager: SettingsManager,
    modelDownloader: ModelDownloader,
    onNavigateToDownload: () -> Unit,
    onBack: () -> Unit,
) {
    val currentVoiceId = settingsManager.getVoiceId()
    var selectedVoice by remember {
        mutableStateOf(
            AVAILABLE_VOICES.find { it.id == currentVoiceId } ?: AVAILABLE_VOICES.first()
        )
    }
    var saved by remember { mutableStateOf(false) }
    val downloadableVoices = AVAILABLE_VOICES.filter { it.id != "android_native" }
    val missingCount = downloadableVoices.count { !modelDownloader.isModelDownloaded(it.id) }
    val isHotwordReady = modelDownloader.isHotwordModelDownloaded()
    FuturisticBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SubScreenHeader(
                    icon = "\ud83d\udde3\ufe0f",
                    title = "Voz de la IA",
                    subtitle = "Elige c\u00f3mo suena Orion",
                    onBack = onBack,
                )
            }
            items(AVAILABLE_VOICES) { voice ->
                val isSelected = selectedVoice == voice
                val isDownloaded =
                    voice.id == "android_native" || modelDownloader.isModelDownloaded(voice.id)
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) NeonBlue.copy(alpha = 0.12f) else Color(0xFF0D0D1A)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) NeonBlue else NeonBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable(enabled = isDownloaded) {
                                selectedVoice = voice
                                saved = false
                            }
                            .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (isDownloaded) 1f else 0.5f),
                    ) {
                        Text(
                            voice.emoji,
                            fontSize = 30.sp,
                            modifier = Modifier.padding(end = 14.dp),
                        "Concede los permisos de \"Dispositivos cercanos\" (para Bluetooth) y/o \"Ubicación\" (para detectar redes WiFi).",
                        "Sin esto, los eventos personalizados podrían fallar silenciosamente."
                    ),
                    buttonText = "Abrir Ajustes de la App",
                    isGranted = hasAppPerms,
                    onButtonClick = { openSystemSettings() }
                )
            }
        }
    }
}

// ── Sub-screen: SILENCE DETECTION ─────────────────────────────────────────────
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun SilenceSubScreen(onBack: () -> Unit) {
    var silenceSeconds by remember { mutableStateOf(2f) }
    var volumeThreshold by remember { mutableStateOf(0.10f) }

    FuturisticBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp)
        ) {
            item {
                SubScreenHeader(
                    icon = "ðŸŽ™ï¸ ",
                    title = "DetecciÃ³n de Silencio",
                    subtitle = "CuÃ¡ndo Orion deja de escucharte",
                    onBack = onBack
                )
                InfoBox("Si el volumen cae por debajo del umbral durante mÃ¡s de ${String.format("%.1f", silenceSeconds)}s, tu mensaje se envÃ­a automÃ¡ticamente.")
                Spacer(Modifier.height(24.dp))

                SliderRow(
                    label = "Tiempo de silencio",
                    value = silenceSeconds,
                    valueLabel = String.format("%.1f s", silenceSeconds),
                    range = 0.5f..5f, steps = 8,
                    onValueChange = { silenceSeconds = it }
                )
                Spacer(Modifier.height(20.dp))
                SliderRow(
                    label = "Umbral de volumen",
                    value = volumeThreshold,
                    valueLabel = "${(volumeThreshold * 100).toInt()}%",
                    range = 0.02f..0.30f, steps = 0,
                    onValueChange = { volumeThreshold = it }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-screen: VOICE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun VoiceSubScreen(
    settingsManager: SettingsManager,
    modelDownloader: ModelDownloader,
    onNavigateToDownload: () -> Unit,
    onBack: () -> Unit
) {
    val currentVoiceId = settingsManager.getVoiceId()
    var selectedVoice by remember {
        mutableStateOf(AVAILABLE_VOICES.find { it.id == currentVoiceId } ?: AVAILABLE_VOICES.first())
    }
    var saved by remember { mutableStateOf(false) }
    val downloadableVoices = AVAILABLE_VOICES.filter { it.id != "android_native" }
    val missingCount = downloadableVoices.count { !modelDownloader.isModelDownloaded(it.id) }
    val isHotwordReady = modelDownloader.isHotwordModelDownloaded()

    FuturisticBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 60.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SubScreenHeader(
                    icon = "🗣️",
                    title = "Voz de la IA",
                    subtitle = "Elige cómo suena Orion",
                    onBack = onBack
                )
            }

            items(AVAILABLE_VOICES) { voice ->
                val isSelected = selectedVoice == voice
                val isDownloaded = voice.id == "android_native" || modelDownloader.isModelDownloaded(voice.id)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) NeonBlue.copy(alpha = 0.12f) else Color(0xFF0D0D1A))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) NeonBlue else NeonBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable(enabled = isDownloaded) { selectedVoice = voice; saved = false }
                        .padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (isDownloaded) 1f else 0.5f)
                    ) {
                        Text(voice.emoji, fontSize = 30.sp, modifier = Modifier.padding(end = 14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    voice.displayName, fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) NeonBlue else Color(0xFFE0E0E0)
                                )
                                Spacer(Modifier.width(8.dp))
                                // Download status badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isDownloaded) Color(0xFF00FF88).copy(alpha = 0.15f)
                                            else Color(0xFFFF9800).copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        if (isDownloaded) "âœ“ Instalada" else "â†¯ Falta descargar",
                                        fontSize = 10.sp,
                                        color = if (isDownloaded) Color(0xFF00FF88) else Color(0xFFFF9800),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "${voice.gender} Â· ${voice.description}",
                                fontSize = 12.sp, color = Color(0xFF707080),
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
private fun TutorialCard(
    icon: String,
    title: String,
    accentColor: Color,
    steps: List<String>,
    buttonText: String,
    isGranted: Boolean = false,
    onButtonClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0D0D1A))
                .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Spacer(Modifier.weight(1f))
            if (isGranted)
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00FF88).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "\u2713 Concedido",
                        color = Color(0xFF00FF88),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
        }
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier =
                        Modifier.size(22.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    fontSize = 13.sp,
                    color = Color(0xFFB0B0C0),
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.15f),
                    contentColor = accentColor,
                ),
        ) {
            Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        }
    }
}

// â”€â”€ Tutorial Card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun TutorialCard(
    icon: String,
    title: String,
    accentColor: Color,
    steps: List<String>,
    buttonText: String,
    isGranted: Boolean = false,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D0D1A))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
        }

        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}", fontSize = 10.sp,
                        fontWeight = FontWeight.Black, color = accentColor
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(step, fontSize = 13.sp, color = Color(0xFFB0B0C0), lineHeight = 18.sp, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor.copy(alpha = 0.15f),
                contentColor = accentColor
            )
        ) {
            Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

// â”€â”€ Reusable InfoBox â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun InfoBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeonBlue.copy(alpha = 0.05f))
            .border(1.dp, NeonBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(text, fontSize = 12.sp, color = Color(0xFFA0A0B0), lineHeight = 18.sp)
    }
}

// â”€â”€ Shared: SliderRow â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun SliderRow(
    label: String, value: Float, valueLabel: String,
    range: ClosedFloatingPointRange<Float>, steps: Int,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFE0E0E0), fontSize = 14.sp)
        Text(valueLabel, color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value, onValueChange = onValueChange,
        valueRange = range, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = NeonBlue, activeTrackColor = NeonBlue,
            inactiveTrackColor = NeonBlue.copy(alpha = 0.2f)
        )
    )
}

// â”€â”€ Shared: FactItem â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun FactItem(index: Int, text: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D0D1A))
            .border(1.dp, NeonBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("$index.", fontSize = 12.sp, color = NeonBlue.copy(alpha = 0.5f),
                modifier = Modifier.padding(end = 10.dp), fontWeight = FontWeight.Bold)
            Text(text = text, fontSize = 13.sp, color = Color(0xFFD0D0E0), lineHeight = 18.sp)
        }
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape)
                .background(Color(0xFF2A0A0A)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("âœ•", fontSize = 13.sp, color = Color(0xFFFF5555))
        }
    }
}

// â”€â”€ Events SubScreen â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
internal fun EventsSubScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    var rules by remember { mutableStateOf(settingsManager.getEventRules()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<org.example.project.model.EventRule?>(null) }

    fun saveRules(newRules: List<org.example.project.model.EventRule>) {
        rules = newRules
        settingsManager.saveEventRules(newRules)
    }

    FuturisticBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // LazyColumn moved before back button
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 120.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "EVENTOS",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonBlue,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "Activa a Orion automÃ¡ticamente segÃºn el contexto",
                        fontSize = 14.sp,
                        color = NeonBlue.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )
                }

                if (rules.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0D0D1A))
                                .border(1.dp, NeonBlue.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ðŸ“¡", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "AÃºn no hay eventos configurados",
                                    fontSize = 15.sp, color = Color(0xFF8080A0),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Pulsa el botÃ³n + para aÃ±adir uno",
                                    fontSize = 13.sp, color = NeonBlue.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(top = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(rules, key = { it.id }) { rule ->
                    val typeLabel = when (rule.type) {
                        org.example.project.model.EventType.BLUETOOTH_CONNECTED -> "ðŸ”µ Bluetooth"
                        org.example.project.model.EventType.WIFI_CONNECTED -> "ðŸ“¶ WiFi"
                        org.example.project.model.EventType.POWER_CONNECTED -> "âš¡ Cargador conectado"
                        org.example.project.model.EventType.POWER_DISCONNECTED -> "ðŸ”‹ Cargador desconectado"
                        org.example.project.model.EventType.BATTERY_LOW -> "ðŸª« BaterÃ­a baja"
                        org.example.project.model.EventType.AIRPLANE_MODE_ON -> "âœˆï¸ Modo aviÃ³n act."
                        org.example.project.model.EventType.AIRPLANE_MODE_OFF -> "ðŸ“¶ Modo aviÃ³n desact."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (rule.isEnabled) Color(0xFF0D0D1A) else Color(0xFF0A0A0F))
                            .border(
                                1.dp,
                                if (rule.isEnabled) NeonBlue.copy(alpha = 0.25f) else NeonBlue.copy(alpha = 0.05f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (rule.isEnabled) Color(0xFFE0E0F8) else Color(0xFF6060808))
                            Text("$typeLabel Â· ${rule.matchValue}", fontSize = 12.sp, color = NeonBlue.copy(alpha = 0.5f), modifier = Modifier.padding(top = 2.dp))
                        }
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { checked ->
                                saveRules(rules.map { if (it.id == rule.id) it.copy(isEnabled = checked) else it })
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonBlue,
                                checkedTrackColor = NeonBlue.copy(alpha = 0.25f),
                                uncheckedThumbColor = Color(0xFF4040608),
                                uncheckedTrackColor = Color(0xFF1A1A2E)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Color(0xFF1A0A0A))
                                .clickable {
                                    saveRules(rules.filter { it.id != rule.id })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("âœ•", fontSize = 14.sp, color = Color(0xFFFF5555))
                        }
                    }
                }
            }

            // FAB to add new event
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeonBlue.copy(alpha = 0.9f), Color(0xFF0057FF))
                        )
                    )
                    .clickable { editingRule = null; showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            // Back button drawn on top
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 52.dp, start = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141428).copy(alpha = 0.8f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
            ) {
                Text("â€¹ Volver", color = NeonBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }

    if (showDialog) {
        EventCreationDialog(
            initial = editingRule,
            onDismiss = { showDialog = false },
            onConfirm = { newRule ->
                showDialog = false
                val existing = rules.indexOfFirst { it.id == newRule.id }
                saveRules(
                    if (existing >= 0) rules.toMutableList().also { it[existing] = newRule }
                    else rules + newRule
                )
            }
        )
    }
}

@Composable
private fun EventCreationDialog(
    initial: org.example.project.model.EventRule?,
    onDismiss: () -> Unit,
    onConfirm: (org.example.project.model.EventRule) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedType by remember { mutableStateOf(initial?.type ?: org.example.project.model.EventType.BLUETOOTH_CONNECTED) }
    var matchValue by remember { mutableStateOf(initial?.matchValue ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D1A),
        titleContentColor = NeonBlue,
        textContentColor = Color(0xFFD0D0E0),
        title = { Text(if (initial == null) "Nuevo Evento" else "Editar Evento", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del evento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        org.example.project.model.EventType.POWER_DISCONNECTED to "ðŸ”‹ Cargador desact.",
                        org.example.project.model.EventType.BATTERY_LOW to "ðŸª« BaterÃ­a baja",
                        org.example.project.model.EventType.AIRPLANE_MODE_ON to "âœˆï¸ AviÃ³n activado",
                        org.example.project.model.EventType.AIRPLANE_MODE_OFF to "ðŸ“¶ AviÃ³n desactivado"
                    ).forEach { (type, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedType == type) NeonBlue.copy(alpha = 0.2f) else Color(0xFF141428))
                                .border(1.dp, if (selectedType == type) NeonBlue else NeonBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { selectedType = type }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 13.sp, color = if (selectedType == type) NeonBlue else Color(0xFF8080A0))
                        }
                    }
                }

                val needsMatchValue = selectedType == org.example.project.model.EventType.BLUETOOTH_CONNECTED || selectedType == org.example.project.model.EventType.WIFI_CONNECTED
                if (needsMatchValue) {
                    val hint = when (selectedType) {
                        org.example.project.model.EventType.BLUETOOTH_CONNECTED -> "Nombre exacto del dispositivo BT (ej. 'Galaxy Buds')"
                        org.example.project.model.EventType.WIFI_CONNECTED -> "Nombre exacto de la red WiFi (ej. 'Mi Casa 5G')"
                        else -> ""
                    }

                    OutlinedTextField(
                        value = matchValue,
                        onValueChange = { matchValue = it },
                        label = { Text(hint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = NeonBlue.copy(alpha = 0.3f),
                            focusedLabelColor = NeonBlue,
                            cursorColor = NeonBlue,
                            focusedTextColor = Color(0xFFE0E0F8),
                            unfocusedTextColor = Color(0xFFE0E0F8)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalMatchValue = if (selectedType == org.example.project.model.EventType.BLUETOOTH_CONNECTED || selectedType == org.example.project.model.EventType.WIFI_CONNECTED) matchValue else "N/A"
                    if (name.isNotBlank() && finalMatchValue.isNotBlank()) {
                        onConfirm(
                            org.example.project.model.EventRule(
                                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name,
                                type = selectedType,
                                matchValue = finalMatchValue
                            )
                        )
                    }
                }
            ) {
                Text("Guardar", color = NeonBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF8080A0))
            }
        }
    )
}


                                type = selectedType,
                                matchValue = finalMatchValue
                            )
                        )
                    }
                }
            ) {
                Text("Guardar", color = NeonBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF8080A0))
            }
        }
    )
}

