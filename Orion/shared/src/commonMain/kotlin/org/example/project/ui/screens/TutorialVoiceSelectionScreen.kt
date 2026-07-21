package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.example.project.ui.theme.NeonBlue
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.DeepBlack

// Available Piper TTS voices for Spanish
data class PiperVoice(
    val id: String,
    val displayName: String,
    val gender: String,
    val emoji: String,
    val description: String
)

val AVAILABLE_VOICES = listOf(
    PiperVoice("claude", "Sara", "Femenina", "👸", "Voz femenina clara (Español Latino)"),
    PiperVoice("davefx", "David", "Masculina", "👨", "Voz masculina profunda y directa"),
    PiperVoice("carlfm", "Carlos", "Masculina", "🧔", "Voz masculina neutra y profesional"),
    PiperVoice("sharvard", "Sharvard", "Masculina", "🎙️", "Voz masculina clara y expresiva"),
    PiperVoice("android_native", "Sistema", "Voz de Android", "🤖", "Sintetizador por defecto del móvil (Rápido pero menos natural)")
)

// 10 sample phrases Orion will say when previewing a voice
val PREVIEW_PHRASES = listOf(
    "Hola, soy Orion, tu asistente de inteligencia artificial.",
    "Estoy aquí para ayudarte en todo lo que necesites.",
    "Puedo responder tus preguntas, recordar cosas y mucho más.",
    "Juntos podemos lograr grandes cosas.",
    "¿En qué puedo ayudarte hoy?",
    "Siempre estoy disponible para ti.",
    "Mi misión es hacerte la vida más sencilla.",
    "Con gusto te asisto en lo que necesites.",
    "Cuéntame, ¿qué tienes en mente?",
    "Soy Orion. Listo para empezar."
)

@Composable
fun TutorialVoiceSelectionScreen(
    onVoiceSelected: (String) -> Unit
) {
    var selectedVoice by remember { mutableStateOf<PiperVoice?>(null) }
    var previewingVoice by remember { mutableStateOf<PiperVoice?>(null) }

    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "MI VOZ",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Elige cómo quieres que suene Orion",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 40.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(AVAILABLE_VOICES) { voice ->
                    val isSelected = selectedVoice == voice
                    val isPreviewing = previewingVoice == voice
                    val borderColor = if (isSelected) NeonBlue else NeonBlue.copy(alpha = 0.2f)
                    val bgColor = if (isSelected) NeonBlue.copy(alpha = 0.15f) else Color(0xFF141414).copy(alpha = 0.6f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedVoice = voice }
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = voice.emoji,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${voice.gender} · ${voice.description}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            // Preview button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isPreviewing) NeonBlue.copy(alpha = 0.3f)
                                        else NeonBlue.copy(alpha = 0.1f)
                                    )
                                    .border(1.dp, NeonBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        previewingVoice = voice
                                        // TODO: Trigger Piper TTS to say a random preview phrase
                                        val phrase = PREVIEW_PHRASES.random()
                                        println("PREVIEW TTS: $phrase with voice ${voice.id}")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPreviewing) "🔊" else "▶",
                                    fontSize = 16.sp,
                                    color = NeonBlue
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Info text about voice downloads
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeonBlue.copy(alpha = 0.08f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "💡 Las voces Sara, David y Carlos requieren descargar modelos (~50MB cada uno). La voz 'Sistema' usa el sintetizador de Android y funciona sin descargas.",
                    fontSize = 12.sp,
                    color = NeonBlue.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    selectedVoice?.let { onVoiceSelected(it.id) }
                },
                enabled = selectedVoice != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonBlue,
                    contentColor = DeepBlack,
                    disabledContainerColor = NeonBlue.copy(alpha = 0.3f),
                    disabledContentColor = DeepBlack.copy(alpha = 0.5f)
                )
            ) {
                Text("ELEGIR ESTA VOZ", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

