package org.example.project.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.project.audio.ModelDownloader
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.NeonBlue
import org.example.project.ui.theme.DeepBlack

// All downloadable piper voices (excludes android_native which needs no download)
private val DOWNLOADABLE_VOICES = AVAILABLE_VOICES.filter { it.id != "android_native" }

@Composable
fun TutorialModelDownloadScreen(
    modelDownloader: ModelDownloader,
    onDownloadComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Which voices are already downloaded (checked on first composition)
    val alreadyDownloaded = remember {
        DOWNLOADABLE_VOICES.filter { modelDownloader.isModelDownloaded(it.id) }.map { it.id }.toSet()
    }
    var isHotwordReady by remember { mutableStateOf(modelDownloader.isHotwordModelDownloaded()) }

    // User selection state: pre-select already downloaded voices (they can't be deselected)
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            DOWNLOADABLE_VOICES.forEach { voice ->
                put(voice.id, false) // nothing pre-selected initially (user chooses)
            }
        }
    }

    // Download state
    var downloadStarted  by remember { mutableStateOf(false) }
    var isDownloading    by remember { mutableStateOf(false) }
    var isComplete       by remember { mutableStateOf(false) }
    var downloadError    by remember { mutableStateOf<String?>(null) }
    var currentLabel     by remember { mutableStateOf("") }
    var progress         by remember { mutableStateOf(0f) }
    var currentVoiceId   by remember { mutableStateOf("") }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    fun startDownload() {
        if (isDownloading || isComplete) return

        val needsHotwordModel = !isHotwordReady

        // Voices to download: selected ones that aren't already downloaded.
        // If user selected nothing, download ALL that aren't already downloaded.
        val userSelected = selected.entries.filter { it.value }.map { it.key }
        val toDownload = if (userSelected.isEmpty()) {
            DOWNLOADABLE_VOICES.map { it.id }.filter { !alreadyDownloaded.contains(it) }
        } else {
            userSelected.filter { !alreadyDownloaded.contains(it) }
        }

        if (toDownload.isEmpty() && !needsHotwordModel) {
            isComplete = true
            return
        }

        downloadStarted = true
        isDownloading = true
        downloadError = null
        progress = 0f

        scope.launch {
            var anyVoiceSuccess = false
            var hotwordSuccess = isHotwordReady
            val failedVoices = mutableListOf<String>()

            if (needsHotwordModel) {
                currentVoiceId = "__hotword__"
                currentLabel = "Preparando activacion por voz: Orion"
                progress = 0f
                hotwordSuccess = try {
                    modelDownloader.downloadHotwordModel { p -> progress = p }
                } catch (e: Exception) {
                    false
                }
                isHotwordReady = hotwordSuccess
            }

            if (hotwordSuccess) {
                for ((index, voiceId) in toDownload.withIndex()) {
                    currentVoiceId = voiceId
                    val voiceName = AVAILABLE_VOICES.find { it.id == voiceId }?.displayName ?: voiceId
                    currentLabel = "Descargando $voiceName (${index + 1}/${toDownload.size})"
                    progress = 0f

                    try {
                        val success = modelDownloader.downloadModel(voiceId) { p -> progress = p }
                        if (!success) failedVoices.add(voiceId) else anyVoiceSuccess = true
                    } catch (e: Exception) {
                        failedVoices.add(voiceId)
                    }
                }
            }

            isDownloading = false
            when {
                !hotwordSuccess -> {
                    isComplete = false
                    downloadError = "No se pudo descargar el modelo que escucha 'Orion'. Sin el, los eventos no pueden activar el asistente."
                }
                failedVoices.isEmpty() -> {
                    isComplete = true
                    downloadError = null
                }
                anyVoiceSuccess || toDownload.size > failedVoices.size -> {
                    isComplete = true
                    downloadError = "Algunas voces fallaron (${failedVoices.size}), pero Orion ya puede continuar."
                }
                else -> {
                    isComplete = true
                    downloadError = "Las voces no se pudieron descargar, pero Orion ya puede usar la voz del sistema."
                }
            }
        }
    }
    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NeonBlue.copy(alpha = 0.12f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("ðŸ—£ï¸", fontSize = 36.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Modelos de Voz",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Elige las voces que quieres descargar. Tambien se preparara el modelo que permite escuchar Orion solo cuando un evento lo active.\nSi no marcas ninguna voz, se descargaran todas las que falten (~40 MB por voz).",
                fontSize = 13.sp,
                color = Color(0xFFA0A0B0),
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(28.dp))

            // Voice list with checkboxes
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DOWNLOADABLE_VOICES.forEach { voice ->
                    val isAlreadyDone = alreadyDownloaded.contains(voice.id)
                    val isChecked = isAlreadyDone || (selected[voice.id] == true)
                    val isCurrentlyDownloading = isDownloading && currentVoiceId == voice.id
                    val downloadedAfter = isComplete && modelDownloader.isModelDownloaded(voice.id)
                    val failed = downloadStarted && !isDownloading && isComplete && !modelDownloader.isModelDownloaded(voice.id)

                    val statusIcon = when {
                        failed              -> "âœ—"
                        isAlreadyDone || downloadedAfter -> "âœ“"
                        isCurrentlyDownloading -> "â¬‡"
                        else                -> if (isChecked) "â˜‘" else "â—‹"
                    }
                    val statusColor = when {
                        failed              -> Color(0xFFFF5555)
                        isAlreadyDone || downloadedAfter -> Color(0xFF00FF88)
                        isCurrentlyDownloading -> NeonBlue
                        isChecked           -> NeonBlue
                        else                -> Color(0xFF404050)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isCurrentlyDownloading -> NeonBlue.copy(alpha = 0.07f)
                                    isAlreadyDone          -> Color(0xFF00FF88).copy(alpha = 0.05f)
                                    isChecked              -> NeonBlue.copy(alpha = 0.05f)
                                    else                   -> Color(0xFF0D0D1A)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isCurrentlyDownloading -> NeonBlue.copy(alpha = 0.5f)
                                    isAlreadyDone          -> Color(0xFF00FF88).copy(alpha = 0.3f)
                                    isChecked              -> NeonBlue.copy(alpha = 0.3f)
                                    else                   -> Color(0xFF1A1A2A)
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = !isDownloading && !isComplete && !isAlreadyDone) {
                                selected[voice.id] = !(selected[voice.id] ?: false)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            statusIcon, fontSize = 16.sp, color = statusColor,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(end = 12.dp).width(20.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(voice.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    voice.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCurrentlyDownloading) NeonBlue else Color(0xFFE0E0E0)
                                )
                                if (voice.gender == "Femenina") {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFF69B4).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Femenina", fontSize = 10.sp, color = Color(0xFFFF69B4), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                voice.description,
                                fontSize = 11.sp,
                                color = Color(0xFF606070),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (isAlreadyDone) {
                            Text("Ya descargada", fontSize = 10.sp, color = Color(0xFF00FF88).copy(alpha = 0.7f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Progress bar
            if (downloadStarted && !isComplete) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isDownloading) {
                        Text(
                            text = currentLabel,
                            fontSize = 12.sp,
                            color = NeonBlue.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = NeonBlue,
                            trackColor = NeonBlue.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color(0xFF606070),
                            modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                        )
                    }

                    downloadError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, fontSize = 12.sp, color = Color(0xFFFF5555), textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (isComplete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF00FF88).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Listo. Voces y activacion por eventos preparadas.",
                        fontSize = 13.sp, color = Color(0xFF00FF88), fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            // Action button
            Button(
                onClick = {
                    when {
                        isComplete     -> onDownloadComplete()
                        !isDownloading -> startDownload()
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isComplete) Color(0xFF00FF88) else NeonBlue,
                    contentColor = DeepBlack,
                    disabledContainerColor = NeonBlue.copy(alpha = 0.25f),
                    disabledContentColor = DeepBlack.copy(alpha = 0.4f)
                )
            ) {
                val noneSelected = selected.values.none { it }
                Text(
                    text = when {
                        isDownloading -> "Descargando..."
                        isComplete    -> "Continuar →"
                        downloadError != null -> "Reintentar"
                        noneSelected  -> "Descargar Todas"
                        else          -> "Descargar Selección"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (!isComplete && !isDownloading) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onDownloadComplete() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Omitir - los eventos no escucharan Orion hasta descargar el modelo",
                        fontSize = 13.sp,
                        color = NeonBlue.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
