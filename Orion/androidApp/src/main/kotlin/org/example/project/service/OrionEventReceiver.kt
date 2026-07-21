package org.example.project.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import org.example.project.model.EventRule
import org.example.project.model.EventType
import org.example.project.model.EventCondition

class OrionEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("orion_settings", Context.MODE_PRIVATE)
        val isAlwaysListening = prefs.getBoolean("always_listening", false)
        
        val rulesJson = prefs.getString("event_rules", null) ?: return
        val rules: List<EventRule> = try {
            Json.decodeFromString(rulesJson)
        } catch (e: Exception) { return }

        val enabledRules = rules.filter { it.isEnabled }

        when (intent.action) {
            // ---- PHONE CALL: always stop hotword ----
            "android.intent.action.PHONE_STATE" -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (state != TelephonyManager.EXTRA_STATE_IDLE) {
                    stopHotwordService(context)
                }
            }

            // ---- BLUETOOTH CONNECTED ----
            "android.bluetooth.device.action.ACL_CONNECTED" -> {
                if (!hasBluetoothConnectPermission(context)) return

                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceName = try { device?.name } catch (e: SecurityException) { null } ?: return
                
                checkRules(context, EventType.BLUETOOTH_CONNECTED, deviceName, enabledRules, isAlwaysListening)
            }

            // ---- BLUETOOTH DISCONNECTED ----
            "android.bluetooth.device.action.ACL_DISCONNECTED" -> {
                if (!hasBluetoothConnectPermission(context)) return

                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try { device?.name } catch (e: SecurityException) { null } ?: return
                
                // Fallback auto-stop if there's no explicit rules but it was the start condition
                val hasStartCondition = enabledRules.any { rule ->
                    val cond = rule.startCondition
                    cond?.type == EventType.BLUETOOTH_CONNECTED && namesMatch(cond.matchValue, deviceName)
                }
                
                val ruleExecuted = checkRules(context, EventType.BLUETOOTH_DISCONNECTED, deviceName, enabledRules, isAlwaysListening)
                
                if (!ruleExecuted && hasStartCondition && !isAlwaysListening) {
                    stopHotwordService(context)
                }
            }

            // ---- WIFI STATE CHANGE ----
            "android.net.wifi.STATE_CHANGE" -> {
                if (!hasFineLocationPermission(context)) return

                @Suppress("DEPRECATION")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                val ssid = try {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wm.connectionInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
                } catch (e: Exception) { null }

                if (networkInfo?.isConnected == true && !ssid.isNullOrBlank() && ssid != UNKNOWN_SSID) {
                    checkRules(context, EventType.WIFI_CONNECTED, ssid, enabledRules, isAlwaysListening)
                } else if (networkInfo?.isConnected == false) {
                    val hasStartCondition = enabledRules.any { it.startCondition?.type == EventType.WIFI_CONNECTED }
                    val ruleExecuted = checkRules(context, EventType.WIFI_DISCONNECTED, "N/A", enabledRules, isAlwaysListening)
                    if (!ruleExecuted && hasStartCondition && !isAlwaysListening) {
                        stopHotwordService(context)
                    }
                }
            }

            // ---- POWER / BATTERY / AIRPLANE MODE ----
            "android.intent.action.ACTION_POWER_CONNECTED" -> {
                checkRules(context, EventType.POWER_CONNECTED, "N/A", enabledRules, isAlwaysListening)
            }
            "android.intent.action.ACTION_POWER_DISCONNECTED" -> {
                checkRules(context, EventType.POWER_DISCONNECTED, "N/A", enabledRules, isAlwaysListening)
            }
            "android.intent.action.BATTERY_LOW" -> {
                checkRules(context, EventType.BATTERY_LOW, "N/A", enabledRules, isAlwaysListening)
            }
            "android.intent.action.AIRPLANE_MODE" -> {
                val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                if (isAirplaneModeOn) {
                    checkRules(context, EventType.AIRPLANE_MODE_ON, "N/A", enabledRules, isAlwaysListening)
                } else {
                    checkRules(context, EventType.AIRPLANE_MODE_OFF, "N/A", enabledRules, isAlwaysListening)
                }
            }
        }
    }

    private fun checkRules(
        context: Context, 
        eventType: EventType, 
        matchValue: String, 
        rules: List<EventRule>, 
        isAlwaysListening: Boolean
    ): Boolean {
        var ruleExecuted = false
        
        // Check if any rule starts Orion
        val startsOrion = rules.any { rule ->
            val cond = rule.startCondition
            cond?.type == eventType && namesMatch(cond.matchValue, matchValue)
        }
        if (startsOrion) {
            startHotwordService(context)
            ruleExecuted = true
        }

        // Check if any rule stops Orion
        val stopsOrion = rules.any { rule ->
            val cond = rule.stopCondition
            cond?.type == eventType && namesMatch(cond.matchValue, matchValue)
        }
        if (stopsOrion) {
            ruleExecuted = true
            if (!isAlwaysListening) {
                stopHotwordService(context)
            }
        }
        
        return ruleExecuted
    }

    private fun startHotwordService(context: Context) {
        val serviceIntent = Intent(context, OrionHotwordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopHotwordService(context: Context) {
        val serviceIntent = Intent(context, OrionHotwordService::class.java)
        context.stopService(serviceIntent)
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun namesMatch(ruleValue: String, eventValue: String): Boolean {
        if (ruleValue == "N/A" || eventValue == "N/A") return true
        return ruleValue.trim().equals(eventValue.trim(), ignoreCase = true)
    }

    private companion object {
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
