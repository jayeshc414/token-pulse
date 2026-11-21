package dev.jayesh.tokenpulse.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class LimitWindow(val pct: Double, val resetsAt: Instant?)

data class Plan(
    val tier: String?,
    val fiveHour: LimitWindow?,
    val sevenDay: LimitWindow?,
    val sevenDayOpus: LimitWindow?,
    val sevenDaySonnet: LimitWindow?,
    val extraPct: Double?,
)

data class Totals(
    val cost: Double,
    val input: Long,
    val output: Long,
    val cacheWrite: Long,
    val cacheRead: Long,
    val tokens: Long,
    val messages: Long,
)

data class Block(
    val start: Instant,
    val end: Instant,
    val totals: Totals,
    val burnRatePerHour: Double,
    val tokensPerMinute: Long,
    val projectedCost: Double,
)

data class DayPoint(val date: String, val cost: Double, val tokens: Long)

data class Ranked(val name: String, val cost: Double, val tokens: Long, val share: Double)

data class Snapshot(
    val generatedAt: Instant,
    val host: String,
    val plan: Plan?,
    val planError: String?,
    val block: Block?,
    val today: Totals,
    val sessionsToday: Int,
    val month: Totals,
    val monthLabel: String,
    val daily: List<DayPoint>,
    val models: List<Ranked>,
    val projects: List<Ranked>,
) {
    companion object {
        fun parse(json: String): Snapshot = fromJson(JSONObject(json))

        private fun JSONObject.str(key: String): String? =
            if (has(key) && !isNull(key)) getString(key) else null

        private fun JSONObject.obj(key: String): JSONObject? =
            if (has(key) && !isNull(key)) optJSONObject(key) else null

        private fun JSONObject.num(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

        private fun instant(s: String?): Instant? =
            s?.let { runCatching { Instant.parse(it) }.getOrNull() }

        private fun window(o: JSONObject?): LimitWindow? {
            if (o == null) return null
            val pct = o.num("pct") ?: o.num("utilization") ?: return null
            return LimitWindow(pct, instant(o.str("resetsAt") ?: o.str("resets_at")))
        }

        private fun totals(o: JSONObject?): Totals = Totals(
            cost = o?.optDouble("cost", 0.0) ?: 0.0,
            input = o?.optLong("input", 0) ?: 0,
            output = o?.optLong("output", 0) ?: 0,
            cacheWrite = o?.optLong("cacheWrite", 0) ?: 0,
            cacheRead = o?.optLong("cacheRead", 0) ?: 0,
            tokens = o?.optLong("tokens", 0) ?: 0,
            messages = o?.optLong("messages", 0) ?: 0,
        )

        private fun ranked(a: JSONArray?): List<Ranked> {
            if (a == null) return emptyList()
            return (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Ranked(o.optString("name"), o.optDouble("cost", 0.0), o.optLong("tokens", 0), o.optDouble("share", 0.0))
            }
        }

        fun fromJson(j: JSONObject): Snapshot {
            val planObj = j.obj("plan")
            val plan = planObj?.let {
                Plan(
                    tier = it.str("tier"),
                    fiveHour = window(it.obj("fiveHour")),
                    sevenDay = window(it.obj("sevenDay")),
                    sevenDayOpus = window(it.obj("sevenDayOpus")),
                    sevenDaySonnet = window(it.obj("sevenDaySonnet")),
                    extraPct = it.obj("extra")?.num("pct"),
                )
            }
            val b = j.obj("block")
            val block = b?.let {
                val s = instant(it.str("start"))
                val e = instant(it.str("end"))
                if (s != null && e != null) Block(
                    start = s,
                    end = e,
                    totals = totals(it),
                    burnRatePerHour = it.optDouble("burnRatePerHour", 0.0),
                    tokensPerMinute = it.optLong("tokensPerMinute", 0),
                    projectedCost = it.optDouble("projectedCost", 0.0),
                ) else null
            }
            val dailyArr = j.optJSONArray("daily")
            val daily = if (dailyArr == null) emptyList() else (0 until dailyArr.length()).map { i ->
                val o = dailyArr.getJSONObject(i)
                DayPoint(o.optString("date"), o.optDouble("cost", 0.0), o.optLong("tokens", 0))
            }
            val todayObj = j.obj("today")
            val monthObj = j.obj("month")
            return Snapshot(
                generatedAt = instant(j.str("generatedAt")) ?: Instant.now(),
                host = j.optString("host", "computer"),
                plan = plan,
                planError = j.str("planError"),
                block = block,
                today = totals(todayObj),
                sessionsToday = todayObj?.optInt("sessions", 0) ?: 0,
                month = totals(monthObj),
                monthLabel = monthObj?.optString("label", "") ?: "",
                daily = daily,
                models = ranked(j.optJSONArray("models")),
                projects = ranked(j.optJSONArray("projects")),
            )
        }
    }
}
