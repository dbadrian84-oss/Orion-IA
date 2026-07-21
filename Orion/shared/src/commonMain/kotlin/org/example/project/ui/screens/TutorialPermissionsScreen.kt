package org.example.project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.example.project.ui.theme.FuturisticBackground
import org.example.project.ui.theme.NeonBlue
import org.example.project.openSystemSettings
import org.example.project.openBatterySettings
import org.example.project.openAssistantSettings
import org.example.project.isAssistantRoleGranted
import org.example.project.isBatteryOptimizationIgnored
import org.example.project.hasPermission

@Composable
fun TutorialPermissionsScreen(
    onComplete: () -> Unit
) {
    var hasAssistant by remember { mutableStateOf(false) }
    var hasBattery by remember { mutableStateOf(false) }
    var hasAppPerms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            hasAssistant = isAssistantRoleGranted()
            hasBattery = isBatteryOptimizationIgnored()
            hasAppPerms = hasPermission("android.permission.RECORD_AUDIO")
            delay(1000)
        }
    }

    val allGranted = hasAssistant && hasBattery && hasAppPerms

    FuturisticBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(NeonBlue.copy(alpha = 0.1f))
                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🛡️", fontSize = 40.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PERMISOS DE ORION",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = NeonBlue,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Para que Orion funcione correctamente en segundo plano y como asistente predeterminado, necesita algunos permisos clave. Puedes configurarlos ahora o más tarde en Ajustes.",
                fontSize = 14.sp,
                color = Color(0xFFD0D0E0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
            )

            // Permission 1: Assistant Role
            PermissionCard(
                icon = "🤖",
                title = "Asistente Predeterminado",
                description = "Permite invocar a Orion manteniendo pulsado el botón de inicio o deslizando desde la esquina (reemplaza a Google Assistant).",
                buttonText = "Configurar Asistente",
                isGranted = hasAssistant,
                onClick = { openAssistantSettings() }
            )

            // Permission 2: Battery Optimization
            PermissionCard(
                icon = "🔋",
                title = "Uso de Batería sin Restricciones",
                description = "Evita que Android cierre a Orion cuando intentas hablarle en segundo plano o con la pantalla apagada.",
                buttonText = "Quitar Restricciones",
                isGranted = hasBattery,
                onClick = { openBatterySettings() }
            )

            // Permission 3: Other Permissions
            PermissionCard(
                icon = "⚙️",
                title = "Ajustes de la Aplicación",
                description = "Permite el uso del micrófono para escucharte, notificaciones para mantener el servicio activo, y permisos de Bluetooth/WiFi para los eventos de activación.",
                buttonText = "Ir a Ajustes de la App",
                isGranted = hasAppPerms,
                onClick = { openSystemSettings() }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Action Buttons
            val buttonBg = if (allGranted) {
                Brush.horizontalGradient(listOf(Color(0xFF0057FF), NeonBlue))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF303040), Color(0xFF202030)))
            }
            val buttonTextColor = if (allGranted) Color.White else Color(0xFFA0A0C0)

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(buttonBg)
                    .clickable(enabled = allGranted, onClick = onComplete)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "¡TODO LISTO!",
                    color = buttonTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Continuar y saltar por ahora",
                color = NeonBlue.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(onClick = onComplete)
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: String,
    title: String,
    description: String,
    buttonText: String,
    isGranted: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (isGranted) Color(0xFF00FF88).copy(alpha = 0.5f) else NeonBlue.copy(alpha = 0.15f)
    val bgColor = if (isGranted) Color(0xFF00FF88).copy(alpha = 0.05f) else Color(0xFF0D0D1A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE0E0F8)
                )
                if (isGranted) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("✅", fontSize = 18.sp)
                }
            }
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFFA0A0C0),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                lineHeight = 18.sp
            )
            if (!isGranted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonBlue.copy(alpha = 0.1f))
                        .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = NeonBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "Configurado correctamente",
                    color = Color(0xFF00FF88),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
