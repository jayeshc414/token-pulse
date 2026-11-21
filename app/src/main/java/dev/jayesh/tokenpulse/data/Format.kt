package dev.jayesh.tokenpulse.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    fun money(v: Double): String = when {
        v >= 1000 -> "$" + String.format(Locale.US, "%,.0f", v)
        v >= 100 -> "$" + String.format(Locale.US, "%.0f", v)
        else -> "$" + String.format(Locale.US, "%.2f", v)
    }

    fun tokens(v: Long): String = when {
        v >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", v / 1e9)
        v >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1e6)
        v >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1e3)
        else -> v.toString()
    }

    fun pct(v: Double): String = "${v.coerceIn(0.0, 999.0).toInt()}%"

    /** "2h 14m", "38m", "3d 7h". */
    fun until(t: Instant?, now: Instant = Instant.now()): String {
        if (t == null) return "—"
        val d = Duration.between(now, t)
        if (d.isNegative) return "now"
        val days = d.toDays()
        val h = d.toHours() % 24
        val m = d.toMinutes() % 60
        return when {
            days > 0 -> "${days}d ${h}h"
            d.toHours() > 0 -> "${d.toHours()}h ${m}m"
            else -> "${m.coerceAtLeast(1)}m"
        }
    }

    fun ago(t: Instant, now: Instant = Instant.now()): String {
        val s = Duration.between(t, now).seconds.coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60}m ago"
            s < 86400 -> "${s / 3600}h ago"
            else -> "${s / 86400}d ago"
        }
    }

    fun clock(t: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(t)

    fun weekdayAt(t: Instant?): String {
        if (t == null) return "—"
        val z = t.atZone(ZoneId.systemDefault())
        return z.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " +
            DateTimeFormatter.ofPattern("HH:mm").format(z)
    }

    fun dayLetter(date: String): String = runCatching {
        LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }.getOrDefault("")

    fun tier(t: String?): String = when (t?.lowercase()) {
        null, "" -> "Claude Code"
        "max" -> "Max plan"
        "pro" -> "Pro plan"
        "team" -> "Team plan"
        "enterprise" -> "Enterprise"
        else -> t.replaceFirstChar { it.uppercase() } + " plan"
    }

    fun model(name: String): String {
        val n = name.removePrefix("claude-").replace(Regex("-\\d{8}$"), "")
        val parts = n.split("-")
        if (parts.isEmpty()) return name
        val family = parts[0].replaceFirstChar { it.uppercase() }
        val ver = parts.drop(1).filter { it.all(Char::isDigit) }.joinToString(".")
        return if (ver.isEmpty()) family else "$family $ver"
    }
}
