package dev.jayesh.tokenpulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import dev.jayesh.tokenpulse.MainActivity
import dev.jayesh.tokenpulse.R
import dev.jayesh.tokenpulse.data.Fmt
import dev.jayesh.tokenpulse.data.Repository
import dev.jayesh.tokenpulse.data.Snapshot
import dev.jayesh.tokenpulse.work.Sync
import java.time.Instant

class WideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

class RingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

class LimitsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.updateAll(context)
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sync.now(context)
    }
}

object Palette {
    val accent = Color.parseColor("#E07A52")
    val accentHi = Color.parseColor("#FFA878")
    val amber = Color.parseColor("#F2B35B")
    val amberHi = Color.parseColor("#FFD892")
    val danger = Color.parseColor("#FF5E57")
    val dangerHi = Color.parseColor("#FF9A8F")
    val track = Color.parseColor("#26F3EEE8")

    fun forPct(pct: Double, base: Int, hi: Int): Pair<Int, Int> =
        if (pct >= 90) danger to dangerHi else base to hi
}

/** What the ring shows: plan limits when available, otherwise the local 5h block's elapsed time. */
data class RingModel(
    val outerPct: Double,
    val innerPct: Double?,
    val center: String,
    val caption: String,
    val resetsAt: Instant?,
)

fun Snapshot.ringModel(now: Instant = Instant.now()): RingModel {
    val fh = plan?.fiveHour
    if (fh != null) {
        return RingModel(fh.pct, plan?.sevenDay?.pct, Fmt.pct(fh.pct), "SESSION", fh.resetsAt)
    }
    val b = block
    if (b != null && b.end.isAfter(now)) {
        val total = (b.end.toEpochMilli() - b.start.toEpochMilli()).toDouble()
        val elapsed = (now.toEpochMilli() - b.start.toEpochMilli()).toDouble()
        return RingModel(100.0 * elapsed / total, null, Fmt.money(b.totals.cost), "5H BLOCK", b.end)
    }
    return RingModel(0.0, null, Fmt.money(today.cost), "TODAY", null)
}

object Widgets {

    fun updateAll(context: Context) {
        val snap = Repository(context).cached()
        val mgr = AppWidgetManager.getInstance(context)
        val wide = mgr.getAppWidgetIds(ComponentName(context, WideWidgetProvider::class.java))
        val ring = mgr.getAppWidgetIds(ComponentName(context, RingWidgetProvider::class.java))
        if (wide.isNotEmpty()) {
            val rv = wideViews(context, snap)
            wide.forEach { mgr.updateAppWidget(it, rv) }
        }
        if (ring.isNotEmpty()) {
            val rv = ringViews(context, snap)
            ring.forEach { mgr.updateAppWidget(it, rv) }
        }
        val limits = mgr.getAppWidgetIds(ComponentName(context, LimitsWidgetProvider::class.java))
        if (limits.isNotEmpty()) {
            val rv = limitsViews(context, snap)
            limits.forEach { mgr.updateAppWidget(it, rv) }
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun refresh(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, RefreshReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun RemoteViews.countdown(id: Int, until: Instant?): Boolean {
        if (until == null || !until.isAfter(Instant.now())) {
            setViewVisibility(id, View.GONE)
            return false
        }
        val base = SystemClock.elapsedRealtime() + (until.toEpochMilli() - System.currentTimeMillis())
        setViewVisibility(id, View.VISIBLE)
        setChronometer(id, base, null, true)
        setChronometerCountDown(id, true)
        return true
    }

    private fun wideViews(context: Context, s: Snapshot?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_wide)
        rv.setOnClickPendingIntent(R.id.root, openApp(context))
        rv.setOnClickPendingIntent(R.id.refresh, refresh(context))
        if (s == null) {
            rv.setTextViewText(R.id.ring_value, "—")
            rv.setTextViewText(R.id.line2, "Open Token Pulse to connect")
            rv.setTextViewText(R.id.line3, "")
            rv.setViewVisibility(R.id.countdown, View.GONE)
            rv.setImageViewBitmap(R.id.ring, Render.ring(260, 0.0, null))
            return rv
        }
        val m = s.ringModel()
        rv.setImageViewBitmap(R.id.ring, Render.ring(260, m.outerPct, m.innerPct))
        rv.setTextViewText(R.id.ring_value, m.center)
        rv.setTextViewText(R.id.ring_caption, m.caption)
        rv.setTextViewText(R.id.title, Fmt.tier(s.plan?.tier))
        rv.setTextViewText(R.id.updated, Fmt.clock(s.generatedAt))

        val hasTimer = rv.countdown(R.id.countdown, m.resetsAt)
        rv.setTextViewText(R.id.line1_label, if (hasTimer) "Resets in " else "")
        rv.setViewVisibility(R.id.line1_alt, if (hasTimer) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.line1_alt, if (s.plan?.fiveHour == null) "Plan limits unavailable" else "Window reset")

        val week = s.plan?.sevenDay
        rv.setTextViewText(
            R.id.line2,
            if (week != null) "Weekly ${Fmt.pct(week.pct)} · resets ${Fmt.weekdayAt(week.resetsAt)}"
            else s.block?.let { "Block ${Fmt.money(it.totals.cost)} · ${Fmt.money(it.burnRatePerHour)}/h" } ?: "Weekly —",
        )
        rv.setTextViewText(R.id.line3, "Today ${Fmt.money(s.today.cost)} · ${Fmt.tokens(s.today.tokens)} tokens")
        rv.setImageViewBitmap(R.id.spark, Render.spark(560, 52, s.daily.map { it.cost }))
        return rv
    }

    private fun limitsViews(context: Context, s: Snapshot?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_limits)
        rv.setOnClickPendingIntent(R.id.root, openApp(context))
        val fh = s?.plan?.fiveHour
        val wk = s?.plan?.sevenDay
        if (fh != null) {
            rv.setTextViewText(R.id.s_pct, Fmt.pct(fh.pct))
            rv.setImageViewBitmap(R.id.s_bar, Render.bar(400, 12, fh.pct, Palette.forPct(fh.pct, Palette.accent, Palette.accentHi)))
            rv.countdown(R.id.s_countdown, fh.resetsAt)
        } else {
            rv.setTextViewText(R.id.s_pct, "--")
            rv.setImageViewBitmap(R.id.s_bar, Render.bar(400, 12, 0.0, Palette.accent to Palette.accentHi))
            rv.setViewVisibility(R.id.s_countdown, View.GONE)
        }
        if (wk != null) {
            rv.setTextViewText(R.id.w_pct, Fmt.pct(wk.pct))
            rv.setTextViewText(R.id.w_reset, Fmt.until(wk.resetsAt))
            rv.setImageViewBitmap(R.id.w_bar, Render.bar(400, 12, wk.pct, Palette.forPct(wk.pct, Palette.amber, Palette.amberHi)))
        } else {
            rv.setTextViewText(R.id.w_pct, "--")
            rv.setTextViewText(R.id.w_reset, if (s == null) "open app" else "no plan data")
            rv.setImageViewBitmap(R.id.w_bar, Render.bar(400, 12, 0.0, Palette.amber to Palette.amberHi))
        }
        return rv
    }

    private fun ringViews(context: Context, s: Snapshot?): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_ring)
        rv.setOnClickPendingIntent(R.id.root, openApp(context))
        if (s == null) {
            rv.setImageViewBitmap(R.id.ring, Render.ring(320, 0.0, null))
            rv.setTextViewText(R.id.ring_value, "—")
            rv.setViewVisibility(R.id.countdown, View.GONE)
            return rv
        }
        val m = s.ringModel()
        rv.setImageViewBitmap(R.id.ring, Render.ring(320, m.outerPct, m.innerPct))
        rv.setTextViewText(R.id.ring_value, m.center)
        rv.setTextViewText(R.id.ring_caption, m.caption)
        rv.countdown(R.id.countdown, m.resetsAt)
        return rv
    }
}

/** Bitmap drawing for widgets — RemoteViews can't draw arcs, so we paint them. */
object Render {

    fun ring(size: Int, outerPct: Double, innerPct: Double?): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * 0.085f
        val gap = stroke * 0.55f
        val outer = RectF(stroke, stroke, size - stroke, size - stroke)
        val inner = RectF(outer.left + stroke + gap, outer.top + stroke + gap, outer.right - stroke - gap, outer.bottom - stroke - gap)

        val (o1, o2) = Palette.forPct(outerPct, Palette.accent, Palette.accentHi)
        arc(c, outer, stroke, outerPct, o1, o2)
        if (innerPct != null) {
            val (i1, i2) = Palette.forPct(innerPct, Palette.amber, Palette.amberHi)
            arc(c, inner, stroke * 0.72f, innerPct, i1, i2)
        }
        return bmp
    }

    private fun arc(c: Canvas, r: RectF, w: Float, pct: Double, from: Int, to: Int) {
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w; color = Palette.track; strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(r, 0f, 360f, false, track)
        val sweep = (pct.coerceIn(0.0, 100.0) * 3.6).toFloat()
        if (sweep <= 0.5f) return
        val shader = LinearGradient(r.left, r.top, r.right, r.bottom, from, to, Shader.TileMode.CLAMP)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w * 1.25f; strokeCap = Paint.Cap.ROUND
            this.shader = shader; alpha = 90
            maskFilter = BlurMaskFilter(w * 0.9f, BlurMaskFilter.Blur.NORMAL)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND; this.shader = shader
        }
        c.drawArc(r, -90f, sweep, false, glow)
        c.drawArc(r, -90f, sweep, false, p)
    }

    fun bar(w: Int, h: Int, pct: Double, colors: Pair<Int, Int>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = h / 2f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.track })
        val fill = (w * pct.coerceIn(0.0, 100.0) / 100.0).toFloat()
        if (fill > 0f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colors.first, colors.second, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(RectF(0f, 0f, fill.coerceAtLeast(h.toFloat()), h.toFloat()), r, r, p)
        }
        return bmp
    }

    fun spark(w: Int, h: Int, values: List<Double>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bmp
        val c = Canvas(bmp)
        val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
        val n = values.size
        val slot = w.toFloat() / n
        val bw = slot * 0.62f
        val rad = bw / 2.5f
        val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#40F3EEE8") }
        val hot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Palette.accentHi, Palette.accent, Shader.TileMode.CLAMP)
        }
        values.forEachIndexed { i, v ->
            val bh = (v / max * (h - 4)).toFloat().coerceAtLeast(3f)
            val x = i * slot + (slot - bw) / 2
            c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), rad, rad, if (i == n - 1) hot else dim)
        }
        return bmp
    }
}
