package org.example.project

class DesktopPlatform : Platform {
    override val name: String = "Desktop JVM"
}

actual fun getPlatform(): Platform = DesktopPlatform()

actual fun getCurrentDate(): String {
    return java.time.LocalDate.now().toString()
}

actual fun getCurrentTimeMillis(): Long {
    return System.currentTimeMillis()
}

// No-ops on desktop
actual fun openSystemSettings() {}
actual fun openBatterySettings() {}
actual fun openAssistantSettings() {}
actual fun openInstalledApp(query: String): String? = null

actual fun isAssistantRoleGranted(): Boolean = true
actual fun isBatteryOptimizationIgnored(): Boolean = true
actual fun hasPermission(permission: String): Boolean = true
actual fun getPairedBluetoothDevices(): List<String> = emptyList()
actual fun syncHotwordServiceState(alwaysListening: Boolean, hasEventRules: Boolean) {}

actual fun canDrawOverlays(): Boolean = true
actual fun openOverlaySettings() {}
