package com.example.data

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val indonesiaLocale = Locale("in", "ID")

    fun formatRupiah(amount: Long, showSign: Boolean = false): String {
        val formatter = NumberFormat.getNumberInstance(indonesiaLocale)
        val formatted = formatter.format(abs(amount))
        return when {
            amount < 0 -> "-Rp $formatted"
            showSign && amount > 0 -> "+Rp $formatted"
            else -> "Rp $formatted"
        }
    }

    fun formatCompactRupiah(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val a = abs(amount)
        return when {
            a >= 1_000_000_000L -> String.format(indonesiaLocale, "%sRp %.1fM", sign, a / 1_000_000_000.0)
            a >= 1_000_000L -> String.format(indonesiaLocale, "%sRp %.1fjt", sign, a / 1_000_000.0)
            a >= 1_000L -> String.format(indonesiaLocale, "%sRp %.0frb", sign, a / 1_000.0)
            else -> "${sign}Rp $a"
        }
    }

    fun todayDateKey(): String {
        val cal = Calendar.getInstance()
        return dateKeyFromCalendar(cal)
    }

    fun currentMonthKey(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }

    fun currentTimeLabel(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    fun dateKeyFromCalendar(cal: Calendar): String = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )

    fun dateKeyOffsetFromToday(dayOffset: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        return dateKeyFromCalendar(cal)
    }

    fun monthKeyFromDateKey(dateKey: String): String =
        if (dateKey.length >= 7) dateKey.substring(0, 7) else currentMonthKey()

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, deltaMonths)
        return String.format(
            Locale.US,
            "%04d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )
    }

    fun formatMonthTitle(monthKey: String): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return monthKey
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: return monthKey) - 1
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return SimpleDateFormat("MMMM yyyy", indonesiaLocale).format(cal.time)
            .replaceFirstChar { it.uppercase(indonesiaLocale) }
    }

    fun formatFullDateHeader(dateKey: String): String {
        val today = todayDateKey()
        val yesterday = dateKeyOffsetFromToday(-1)
        val tomorrow = dateKeyOffsetFromToday(1)
        val formatted = formatReadableDate(dateKey)
        return when (dateKey) {
            today -> "Hari Ini • $formatted"
            yesterday -> "Kemarin • $formatted"
            tomorrow -> "Besok • $formatted"
            else -> formatted
        }
    }

    fun formatReadableDate(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return dateKey
        return SimpleDateFormat("EEEE, d MMM yyyy", indonesiaLocale).format(cal.time)
            .replaceFirstChar { it.uppercase(indonesiaLocale) }
    }

    fun formatShortDayName(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return ""
        return SimpleDateFormat("EEE", indonesiaLocale).format(cal.time)
            .replaceFirstChar { it.uppercase(indonesiaLocale) }
    }

    fun formatDayNumber(dateKey: String): String {
        val cal = parseDateKey(dateKey) ?: return ""
        return cal.get(Calendar.DAY_OF_MONTH).toString()
    }

    fun parseDateKey(dateKey: String): Calendar? {
        val parts = dateKey.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun isWithinLastDays(dateKey: String, daysInclusive: Int): Boolean {
        val target = parseDateKey(dateKey) ?: return false
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(daysInclusive - 1))
        }
        val end = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        return !target.before(start) && !target.after(end)
    }

    fun greetingForHour(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..17 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }

    fun slugifyCategory(category: String): String {
        val cleaned = category.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return cleaned.ifBlank { "lainnya" }
    }

    fun parseHexColor(hex: String, fallback: Color = Color(0xFF0F6A53)): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            fallback
        }
    }
}
