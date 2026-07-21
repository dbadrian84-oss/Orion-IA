package org.example.project

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentDate(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date())
}

actual fun getCurrentTimeMillis(): Long {
    return System.currentTimeMillis()
}

// We store the application context reference set from MainActivity
var appContext: android.content.Context? = null

actual fun openSystemSettings() {
    val ctx = appContext ?: return
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general settings
        ctx.startActivity(
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }
}

actual fun openAssistantSettings() {
    val ctx = appContext ?: return
    val intent =
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to manage default apps
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

actual fun openBatterySettings() {
    val ctx = appContext ?: return
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        ctx.startActivity(
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }
}

actual fun isAssistantRoleGranted(): Boolean {
    val ctx = appContext ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager =
            ctx.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
    } else {
        val currentAssistant =
            Settings.Secure.getString(ctx.contentResolver, "voice_interaction_service")
        currentAssistant?.contains(ctx.packageName) == true
    }
}

actual fun isBatteryOptimizationIgnored(): Boolean {
    val ctx = appContext ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    } else {
        true // Not applicable below M
    }
}

actual fun hasPermission(permission: String): Boolean {
    val ctx = appContext ?: return false
    return ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
}

actual fun openInstalledApp(query: String): String? {
    val ctx = appContext ?: return null
    val normalizedQuery = query.normalizedForSearch()
    if (normalizedQuery.isBlank()) return null

    val packageManager = ctx.packageManager
    val launcherIntent =
        Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

    val candidates =
        packageManager
            .queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName ?: return@mapNotNull null
                val label =
                    info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                        ?: packageName
                AppCandidate(label = label, packageName = packageName)
            }
            .distinctBy { it.packageName }

    val best =
        candidates
            .map { it to it.matchScore(normalizedQuery) }
            .filter { (_, score) -> score >= 0.46f }
            .maxByOrNull { (_, score) -> score }
            ?.first ?: return null

    val launchIntent = packageManager.getLaunchIntentForPackage(best.packageName) ?: return null
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        ctx.startActivity(launchIntent)
        best.label
    } catch (_: Exception) {
        null
    }
}

private data class AppCandidate(val label: String, val packageName: String) {
    fun matchScore(query: String): Float {
        val labelNorm = label.normalizedForSearch()
        val packageNorm = packageName.normalizedForSearch()
        val packageLast = packageNorm.substringAfterLast('.')

        return maxOf(
            textScore(labelNorm, query),
            textScore(packageNorm, query) * 0.92f,
            textScore(packageLast, query) * 0.88f,
        )
    }
}

private fun textScore(value: String, query: String): Float {
    if (value == query) return 1f
    if (value.startsWith(query)) return 0.96f
    if (value.contains(query)) return 0.9f
    if (query.contains(value) && value.length >= 4) return 0.82f

    val distance = levenshteinDistance(value, query)
    val longest = max(value.length, query.length).coerceAtLeast(1)
    return (1f - distance.toFloat() / longest.toFloat()).coerceIn(0f, 1f)
}

private fun String.normalizedForSearch(): String {
    val withoutAccents =
        Normalizer.normalize(this, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "")
    return withoutAccents
        .lowercase(Locale.getDefault())
        .replace("[^a-z0-9. ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)

    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        val tmp = previous
        previous = current
        current = tmp
    }

    return previous[b.length]
}

actual fun getPairedBluetoothDevices(): List<String> {
    val ctx = appContext ?: return emptyList()
    val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter ?: return emptyList()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
    }

    return try {
        adapter.bondedDevices?.mapNotNull { it.name } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }
}

actual fun canDrawOverlays(): Boolean {
    val context = appContext ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        android.provider.Settings.canDrawOverlays(context)
    } else {
        true
    }
}

actual fun openOverlaySettings() {
    val context = appContext ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent =
            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun syncHotwordServiceState(alwaysListening: Boolean, hasEventRules: Boolean) {
    val context = appContext ?: return
    val intent = Intent(context, org.example.project.service.OrionHotwordService::class.java)
    if (alwaysListening || hasEventRules) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } else {
        context.stopService(intent)
    }
}
