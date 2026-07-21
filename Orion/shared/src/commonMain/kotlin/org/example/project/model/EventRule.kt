package org.example.project.model

import kotlinx.serialization.Serializable

@Serializable
data class EventCondition(
    val type: EventType,
    val matchValue: String // "N/A" if type doesn't need a value
)

@Serializable
data class EventRule(
    val id: String,
    val name: String,
    val startCondition: EventCondition?, // If null, this rule never starts Orion
    val stopCondition: EventCondition?,  // If null, this rule never stops Orion
    val isEnabled: Boolean = true
)

@Serializable
enum class EventType {
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    WIFI_CONNECTED,
    WIFI_DISCONNECTED,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    BATTERY_LOW,
    AIRPLANE_MODE_ON,
    AIRPLANE_MODE_OFF
}

