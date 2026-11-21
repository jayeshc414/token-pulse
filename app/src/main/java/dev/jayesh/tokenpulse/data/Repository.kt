package dev.jayesh.tokenpulse.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sin

enum class SourceMode { DEMO, GIST, URL }

class Prefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("token_pulse", Context.MODE_PRIVATE)

    var mode: SourceMode
        get() = runCatching { SourceMode.valueOf(p.getString("mode", SourceMode.DEMO.name)!!) }.getOrDefault(SourceMode.DEMO)
        set(v) = p.edit().putString("mode", v.name).apply()
    var gistId: String
        get() = p.getString("gistId", "") ?: ""
        set(v) = p.edit().putString("gistId", v.trim()).apply()
    var githubToken: String
        get() = p.getString("githubToken", "") ?: ""
        set(v) = p.edit().putString("githubToken", v.trim()).apply()
    var customUrl: String
        get() = p.getString("customUrl", "") ?: ""
        set(v) = p.edit().putString("customUrl", v.trim()).apply()
    var refreshMinutes: Int
        get() = p.getInt("refreshMinutes", 15)
        set(v) = p.edit().putInt("refreshMinutes", v).apply()
    var alertsEnabled: Boolean
        get() = p.getBoolean("alerts", true)
        set(v) = p.edit().putBoolean("alerts", v).apply()
    var cachedJson: String?
        get() = p.getString("cache", null)
        set(v) = p.edit().putString("cache", v).apply()
    var lastFetchAt: Long
        get() = p.getLong("lastFetch", 0)
        set(v) = p.edit().putLong("lastFetch", v).apply()
    var lastError: String?
        get() = p.getString("lastError", null)
        set(v) = p.edit().putString("lastError", v).apply()

    /** Highest alert level already sent for the current 5h window, keyed by its reset time. */
    var alertKey: String
        get() = p.getString("alertKey", "") ?: ""
        set(v) = p.edit().putString("alertKey", v).apply()
}

class Repository(context: Context) {
    private val prefs = Prefs(context)

    fun cached(): Snapshot? = when (prefs.mode) {
        SourceMode.DEMO -> Demo.snapshot()
        else -> prefs.cachedJson?.let { runCatching { Snapshot.parse(it) }.getOrNull() }
    }

    suspend fun refresh(): Result<Snapshot> = withContext(Dispatchers.IO) {
        val r = runCatching {
            when (prefs.mode) {
                SourceMode.DEMO -> Demo.snapshot()
                SourceMode.GIST -> {
                    val json = fetchGist(prefs.gistId, prefs.githubToken)
                    Snapshot.parse(json).also { prefs.cachedJson = json }
                }
                SourceMode.URL -> {
                    val json = get(prefs.customUrl, emptyMap())
                    Snapshot.parse(json).also { prefs.cachedJson = json }
                }
            }
        }
        prefs.lastFetchAt = System.currentTimeMillis()
        prefs.lastError = r.exceptionOrNull()?.let { friendly(it) }
        r
    }

    private fun friendly(t: Throwable): String = when {
        t is java.net.UnknownHostException -> "No internet connection"
        t.message?.contains("HTTP 404") == true -> "Gist not found — check the Gist ID"
        t.message?.contains("HTTP 401") == true -> "GitHub token rejected"
        t.message?.contains("HTTP 403") == true -> "GitHub rate limit — add a token in Settings"
        t is org.json.JSONException -> "Snapshot isn't valid Token Pulse JSON"
        else -> t.message ?: t.javaClass.simpleName
    }

    private fun fetchGist(id: String, token: String): String {
        require(id.isNotBlank()) { "Add your Gist ID in Settings" }
        val cleanId = id.substringAfterLast('/').trim()
        val headers = buildMap {
            put("Accept", "application/vnd.github+json")
            put("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) put("Authorization", "Bearer $token")
        }
        val body = JSONObject(get("https://api.github.com/gists/$cleanId", headers))
        val files = body.getJSONObject("files")
        val file = files.optJSONObject("claude-usage.json")
            ?: files.keys().asSequence().firstOrNull()?.let { files.getJSONObject(it) }
            ?: error("Gist has no files")
        if (file.optBoolean("truncated", false)) return get(file.getString("raw_url"), headers)
        return file.getString("content")
    }

    private fun get(url: String, headers: Map<String, String>): String {
        require(url.startsWith("http")) { "Add a valid URL in Settings" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "TokenPulse-Android/1.0")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val code = c.responseCode
            if (code !in 200..299) error("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Realistic sample data so the app and widgets look alive before setup. */
object Demo {
    fun snapshot(): Snapshot {
        val now = Instant.now()
        val today = LocalDate.now()
        val blockStart = now.truncatedTo(ChronoUnit.HOURS).minus(2, ChronoUnit.HOURS)
        val daily = (13 downTo 0).map { i ->
            val c = 6.0 + 5.0 * sin(i * 0.9) + (if (i % 6 == 0) 6.0 else 0.0) + (13 - i) * 0.35
            DayPoint(today.minusDays(i.toLong()).toString(), "%.2f".format(java.util.Locale.US, c.coerceAtLeast(0.6)).toDouble(), (c * 1_150_000).toLong())
        }
        val todayTotals = Totals(14.82, 61_200, 412_800, 1_480_000, 16_930_000, 18_884_000, 312)
        return Snapshot(
            generatedAt = now.minusSeconds(140),
            host = "demo",
            plan = Plan(
                tier = "max",
                fiveHour = LimitWindow(63.0, blockStart.plus(5, ChronoUnit.HOURS)),
                sevenDay = LimitWindow(38.0, now.plus(3, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS)),
                sevenDayOpus = LimitWindow(22.0, now.plus(3, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS)),
                sevenDaySonnet = LimitWindow(9.0, now.plus(3, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS)),
                extraPct = null,
            ),
            planError = null,
            block = Block(
                start = blockStart,
                end = blockStart.plus(5, ChronoUnit.HOURS),
                totals = Totals(8.41, 30_100, 221_000, 820_000, 9_400_000, 10_471_100, 171),
                burnRatePerHour = 3.12,
                tokensPerMinute = 64_200,
                projectedCost = 15.7,
            ),
            today = todayTotals,
            sessionsToday = 6,
            month = Totals(212.4, 1_204_000, 7_900_000, 28_100_000, 301_000_000, 338_204_000, 6_120),
            monthLabel = today.toString().substring(0, 7),
            daily = daily,
            models = listOf(
                Ranked("claude-opus-5-5", 156.2, 190_000_000, 0.71),
                Ranked("claude-sonnet-5-5", 58.9, 131_000_000, 0.27),
                Ranked("claude-haiku-5-5", 4.4, 88_000_000, 0.02),
            ),
            projects = listOf(
                Ranked("api-gateway", 96.1, 150_000_000, 0.44),
                Ranked("mobile-app", 61.7, 98_000_000, 0.28),
                Ranked("data-pipeline", 38.2, 60_000_000, 0.17),
                Ranked("docs-site", 23.5, 30_000_000, 0.11),
            ),
        )
    }
}
