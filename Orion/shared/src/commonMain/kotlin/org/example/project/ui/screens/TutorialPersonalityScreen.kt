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
import org.example.project.model.Personality
import org.example.project.ui.theme.NeonBlue
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.DeepBlack

private fun Personality.emoji(): String = when (this) {
    Personality.ALEGRE -> "😄"
    Personality.SHY -> "😶"
    Personality.SARCASTIC -> "😏"
    Personality.PROFESSIONAL -> "💼"
}

private fun Personality.subtitle(): String = when (this) {
    Personality.ALEGRE -> "Buen rollo y optimismo"
    Personality.SHY -> "Reservado pero servicial"
    Personality.SARCASTIC -> "Humor afilado"
    Personality.PROFESSIONAL -> "Directo y eficiente"
}

@Composable
fun TutorialPersonalityScreen(
    onPersonalitySelected: (Personality) -> Unit
) {
    var selected by remember { mutableStateOf<Personality?>(null) }

    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "PERSONALIDAD",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "¿Cómo quieres que sea Orion?",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 40.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(Personality.entries.toList()) { personality ->
                    val isSelected = selected == personality
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
                            .clickable { selected = personality }
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = personality.emoji(),
                                fontSize = 32.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column {
                                Text(
                                    text = personality.displayName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonBlue else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = personality.subtitle(),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    selected?.let { onPersonalitySelected(it) }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonBlue,
                    contentColor = DeepBlack,
                    disabledContainerColor = NeonBlue.copy(alpha = 0.3f),
                    disabledContentColor = DeepBlack.copy(alpha = 0.5f)
                )
            ) {
                Text("ELEGIR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
