package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.theme.NeonBlue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.GlassCard
import org.example.project.ui.theme.DeepBlack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialUserDetailsScreen(
    onDetailsSubmitted: (name: String, gender: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Hombre") }
    
    val genders = listOf("Hombre", "Mujer", "Otro")

    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "PERFIL",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                modifier = Modifier.padding(bottom = 16.dp),
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Dime tu nombre y cómo te identificas.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tu Nombre") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonBlue.copy(alpha = 0.3f),
                    focusedLabelColor = NeonBlue,
                    cursorColor = NeonBlue
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Género",
                color = NeonBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp, start = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                genders.forEach { g ->
                    val isSelected = gender == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clickable { gender = g },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            // Solid glowing button for selected
                            Button(
                                onClick = { gender = g },
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                            ) {
                                Text(g, color = DeepBlack, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        } else {
                            // Glass card for unselected
                            GlassCard(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(g, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onDetailsSubmitted(name.trim(), gender)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = DeepBlack)
            ) {
                Text("CONTINUAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}
