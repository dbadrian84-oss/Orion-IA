package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun getCurrentDate(): String
expect fun getCurrentTimeMillis(): Long
expect fun openSystemSettings()
expect fun openBatterySettings()
expect fun openAssistantSettings()
expect fun openInstalledApp(query: String): String?

expect fun isAssistantRoleGranted(): Boolean
expect fun isBatteryOptimizationIgnored(): Boolean
expect fun hasPermission(permission: String): Boolean
expect fun canDrawOverlays(): Boolean
expect fun openOverlaySettings()
expect fun getPairedBluetoothDevices(): List<String>
expect fun syncHotwordServiceState(alwaysListening: Boolean, hasEventRules: Boolean)
