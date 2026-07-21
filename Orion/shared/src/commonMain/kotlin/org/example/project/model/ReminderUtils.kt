package org.example.project.model

fun normalizeReminderDate(rawDate: String): String {
    val trimmed = rawDate.trim()
    val isoMatch = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(trimmed)
    if (isoMatch != null) {
        val (year, month, day) = isoMatch.destructured
        return listOf(year, month.padStart(2, '0'), day.padStart(2, '0')).joinToString("-")
    }

    val spanishMatch = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$").matchEntire(trimmed)
    if (spanishMatch != null) {
        val (day, month, year) = spanishMatch.destructured
        return listOf(year, month.padStart(2, '0'), day.padStart(2, '0')).joinToString("-")
    }

    return trimmed
}

fun isReminderOnDate(reminder: Reminder, isoDate: String): Boolean {
    return normalizeReminderDate(reminder.date) == normalizeReminderDate(isoDate)
}

fun formatReminderDateForUser(rawDate: String): String {
    val normalized = normalizeReminderDate(rawDate)
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(normalized) ?: return rawDate
    val (year, month, day) = match.destructured
    return "$day/$month/$year"
}