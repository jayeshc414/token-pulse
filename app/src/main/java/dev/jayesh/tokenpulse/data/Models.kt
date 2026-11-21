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
    val todayDate: String = "",
    val deviceCount: Int = 1,
) {
    companion object {
        /** Accepts one snapshot object, or an array of them (one per computer) and merges. */
        fun parse(json: String): Snapshot {
            val t = json.trim()
            if (!t.startsWith("[")) return fromJson(JSONObject(t))
            val a = JSONArray(t)
            return merge((0 until a.length()).map { fromJson(a.getJSONObject(it)) })
        }

        private fun addT(a: Totals, b: Totals) = Totals(
            a.cost + b.cost, a.input + b.input, a.output + b.output, a.cacheWrite + b.cacheWrite,
            a.cacheRead + b.cacheRead, a.tokens + b.tokens, a.messages + b.messages,
        )

        private val zero = Totals(0.0, 0, 0, 0, 0, 0, 0)

        private fun mergeRanked(lists: List<List<Ranked>>): List<Ranked> {
            val m = HashMap<String, Pair<Double, Long>>()
            lists.flatten().forEach { r ->
                val o = m[r.name]
                m[r.name] = ((o?.first ?: 0.0) + r.cost) to ((o?.second ?: 0L) + r.tokens)
            }
            val total = m.values.sumOf { it.first }
            return m.entries
                .map { Ranked(it.key, it.value.first, it.value.second, if (total > 0) it.value.first / total else 0.0) }
                .sortedWith(compareByDescending<Ranked> { it.cost }.thenByDescending { it.tokens })
                .take(6)
        }

        /** Adds up several computers. Plan limits are account-wide, so the freshest one wins. */
        fun merge(list: List<Snapshot>): Snapshot {
            require(list.isNotEmpty()) { "No usage files found in the gist" }
            if (list.size == 1) return list[0]
            val now = Instant.now()
            val newest = list.maxBy { it.generatedAt }
            val todayOnes = list.filter { it.todayDate == newest.todayDate }
            val monthOnes = list.filter { it.monthLabel == newest.monthLabel }
            val planSrc = list.filter { it.plan?.fiveHour != null }.maxByOrNull { it.generatedAt }
                ?: list.firstOrNull { it.plan != null }
            val blocks = list.mapNotNull { it.block }.filter { it.end.isAfter(now) }
            val block = if (blocks.isEmpty()) null else Block(
                start = blocks.minOf { it.start },
                end = blocks.maxOf { it.end },
                totals = blocks.map { it.totals }.fold(zero, ::addT),
                burnRatePerHour = blocks.sumOf { it.burnRatePerHour },
                tokensPerMinute = blocks.sumOf { it.tokensPerMinute },
                projectedCost = blocks.sumOf { it.projectedCost },
            )
            val days = HashMap<String, Pair<Double, Long>>()
            list.flatMap { it.daily }.forEach { d ->
                val o = days[d.date]
                days[d.date] = ((o?.first ?: 0.0) + d.cost) to ((o?.second ?: 0L) + d.tokens)
            }
            val daily = days.entries.sortedBy { it.key }.map { DayPoint(it.key, it.value.first, it.value.second) }.takeLast(14)
            return Snapshot(
                generatedAt = newest.generatedAt,
                host = list.map { it.host }.distinct().joinToString(" + "),
                plan = planSrc?.plan,
                planError = if (planSrc?.plan != null) null else newest.planError,
                block = block,
                today = todayOnes.map { it.today }.fold(zero, ::addT),
                sessionsToday = todayOnes.sumOf { it.sessionsToday },
                month = monthOnes.map { it.month }.fold(zero, ::addT),
                monthLabel = newest.monthLabel,
                daily = daily,
                models = mergeRanked(list.map { it.models }),
                projects = mergeRanked(list.map { it.projects }),
                todayDate = newest.todayDate,
                deviceCount = list.size,
            )
        }

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
                todayDate = todayObj?.optString("date", "") ?: "",
            )
        }
    }
}
