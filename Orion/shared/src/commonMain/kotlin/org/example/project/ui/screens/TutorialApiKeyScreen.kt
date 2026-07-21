package org.example.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.ui.theme.NeonBlue

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import org.example.project.ui.theme.FuturisticBackground
import org.jetbrains.compose.resources.painterResource
import orion.shared.generated.resources.Res
import orion.shared.generated.resources.api_step1
import orion.shared.generated.resources.api_step2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialApiKeyScreen(
    onApiKeySubmitted: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "ORION",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                letterSpacing = 4.sp
            )
            
            Text(
                text = "Bienvenido a tu nueva IA.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Tutorial Section
            Text(
                text = "Tutorial Conseguir API",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFCC00),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Paso 1
                Text("Paso 1: Acceder a Google AI Studio y loguearte.", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    text = "Haz clic aquí para abrir la web",
                    color = NeonBlue,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://aistudio.google.com/api-keys?hl=es-419&original_referer=https:%2F%2Fai.google.dev&pli=1&project=gen-lang-client-0994308710")
                    }.padding(start = 8.dp)
                )

                // Paso 2
                Text("Paso 2: Darle a 'Crear clave de API' (Create API key).", color = Color.White, fontWeight = FontWeight.Bold)
                Image(
                    painter = painterResource(Res.drawable.api_step1),
                    contentDescription = "Paso 1 captura",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentScale = ContentScale.FillWidth
                )

                // Paso 3
                Text("Paso 3: Seleccionar un proyecto y darle a 'Crear clave'.", color = Color.White, fontWeight = FontWeight.Bold)
                Image(
                    painter = painterResource(Res.drawable.api_step2),
                    contentDescription = "Paso 2 captura",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentScale = ContentScale.FillWidth
                )

                // Paso 4
                Text("Paso 4: Una vez generada, dale a copiar y pégala aquí abajo.", color = Color.White, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Pega tu API Key de Gemini") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonBlue.copy(alpha = 0.3f),
                    focusedLabelColor = NeonBlue,
                    cursorColor = NeonBlue
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        onApiKeySubmitted(apiKey)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonBlue, 
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("INICIAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}
