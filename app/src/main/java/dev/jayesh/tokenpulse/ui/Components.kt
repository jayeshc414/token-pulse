package dev.jayesh.tokenpulse.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jayesh.tokenpulse.data.DayPoint
import dev.jayesh.tokenpulse.data.Fmt
import dev.jayesh.tokenpulse.data.LimitWindow
import dev.jayesh.tokenpulse.data.Ranked
import dev.jayesh.tokenpulse.data.Totals
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(TP.surfaceHi, TP.surface)))
            .border(1.dp, TP.border, RoundedCornerShape(26.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = TP.muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = TP.faint)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Concentric animated rings: outer = session (5h), inner = weekly. */
@Composable
fun DualRing(
    outerPct: Double,
    innerPct: Double?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val outerAnim = remember { Animatable(0f) }
    val innerAnim = remember { Animatable(0f) }
    LaunchedEffect(outerPct) { outerAnim.animateTo(outerPct.toFloat().coerceIn(0f, 100f), tween(1400, easing = FastOutSlowInEasing)) }
    LaunchedEffect(innerPct) { innerAnim.animateTo((innerPct ?: 0.0).toFloat().coerceIn(0f, 100f), tween(1700, 150, FastOutSlowInEasing)) }
    val (o1, o2) = TP.forPct(outerPct, TP.accent, TP.accentHi)
    val (i1, i2) = TP.forPct(innerPct ?: 0.0, TP.amber, TP.amberHi)

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.minDimension * 0.075f
            val inset = w * 1.4f
            ring(inset, w, outerAnim.value, o1, o2)
            if (innerPct != null) ring(inset + w * 1.75f, w * 0.7f, innerAnim.value, i1, i2)
        }
        content()
    }
}

private fun DrawScope.ring(inset: Float, w: Float, pct: Float, from: Color, to: Color) {
    val d = size.minDimension - inset * 2
    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
    val sz = Size(d, d)
    drawArc(TP.track, 0f, 360f, false, tl, sz, style = Stroke(w))
    val sweep = pct * 3.6f
    if (sweep < 0.5f) return
    val brush = Brush.sweepGradient(
        0f to from, (sweep / 360f).coerceAtLeast(0.01f) to to, 1f to to,
        center = Offset(size.width / 2, size.height / 2),
    )
    // Rotate so the gradient starts at 12 o'clock with the arc.
    rotate(degrees = -90f, pivot = center) {
        for (k in 3 downTo 1) {
            drawArc(brush, 0f, sweep, false, tl, sz, alpha = 0.07f * k, style = Stroke(w + w * 0.55f * k, cap = StrokeCap.Round))
        }
        drawArc(brush, 0f, sweep, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        // Bright head
        val r = d / 2
        val a = Math.toRadians(sweep.toDouble())
        val head = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
        drawCircle(Color.White.copy(alpha = 0.85f), w * 0.22f, head)
    }
}

@Composable
fun LimitRow(label: String, w: LimitWindow, color: Color, hi: Color, resetText: String) {
    val anim by animateFloatAsState(w.pct.toFloat().coerceIn(0f, 100f) / 100f, tween(1200, easing = FastOutSlowInEasing), label = "limit")
    val (c1, c2) = TP.forPct(w.pct, color, hi)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TP.text, modifier = Modifier.weight(1f))
            Text(Fmt.pct(w.pct), style = MaterialTheme.typography.bodyMedium, color = c2)
        }
        Spacer(Modifier.height(7.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val cr = CornerRadius(size.height / 2)
            drawRoundRect(TP.track, cornerRadius = cr)
            if (anim > 0f) drawRoundRect(
                Brush.horizontalGradient(listOf(c1, c2)),
                size = Size((size.width * anim).coerceAtLeast(size.height), size.height),
                cornerRadius = cr,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(resetText, style = MaterialTheme.typography.bodySmall, color = TP.faint)
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(TP.surface)
            .border(1.dp, TP.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent, 6.dp)
            Spacer(Modifier.width(7.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TP.muted)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = TP.text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = TP.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 14-day spend bars. Tap a bar to inspect it; today is highlighted. */
@Composable
fun DailyBars(days: List<DayPoint>, modifier: Modifier = Modifier) {
    if (days.isEmpty()) {
        Text("No activity yet", color = TP.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(days) { grow.snapTo(0f); grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val max = (days.maxOf { it.cost }).coerceAtLeast(0.01)
    val sel = days[selected.coerceIn(0, days.lastIndex)]

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.money(sel.cost), style = MaterialTheme.typography.titleLarge, color = TP.text)
            Spacer(Modifier.width(8.dp))
            Text(
                (if (selected == days.lastIndex) "today" else sel.date.substring(5)) + " · " + Fmt.tokens(sel.tokens) + " tok",
                style = MaterialTheme.typography.bodySmall, color = TP.muted, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(days) {
                    detectTapGestures { p ->
                        val slot = size.width / days.size.toFloat()
                        selected = (p.x / slot).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            val slot = size.width / days.size
            val bw = slot * 0.58f
            // guide lines
            for (g in 1..3) {
                val y = size.height * g / 4f
                drawLine(TP.track, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            days.forEachIndexed { i, d ->
                val h = (d.cost / max).toFloat() * size.height * grow.value
                val x = i * slot + (slot - bw) / 2
                val isSel = i == selected
                val isToday = i == days.lastIndex
                val brush = when {
                    isSel -> Brush.verticalGradient(listOf(TP.accentHi, TP.accent), startY = size.height - h, endY = size.height)
                    isToday -> Brush.verticalGradient(listOf(TP.accent.copy(alpha = 0.7f), TP.accent.copy(alpha = 0.35f)))
                    else -> Brush.verticalGradient(listOf(TP.text.copy(alpha = 0.28f), TP.text.copy(alpha = 0.12f)))
                }
                drawRoundRect(
                    brush,
                    topLeft = Offset(x, size.height - h.coerceAtLeast(4f)),
                    size = Size(bw, h.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(bw / 2.6f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    Fmt.dayLetter(d.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (i == selected) TP.accentHi else TP.faint,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Stacked bar of input / output / cache write / cache read. */
@Composable
fun TokenMix(t: Totals) {
    val parts = listOf(
        Triple("Cache read", t.cacheRead, TP.sky),
        Triple("Cache write", t.cacheWrite, TP.sage),
        Triple("Output", t.output, TP.accent),
        Triple("Input", t.input, TP.amber),
    )
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val growAnim = remember { Animatable(0f) }
    LaunchedEffect(t) { growAnim.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val grow = growAnim.value
    Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        var x = 0f
        drawRect(TP.track)
        parts.forEach { (_, v, c) ->
            val w = size.width * v / total * grow
            if (w > 0f) {
                drawRect(c, topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w + if (w > 2f) 2f else 0f
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    parts.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            row.forEach { (name, v, c) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Dot(c)
                    Spacer(Modifier.width(8.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall, color = TP.muted, modifier = Modifier.weight(1f))
                    Text(Fmt.tokens(v), style = MaterialTheme.typography.bodySmall, color = TP.text, modifier = Modifier.padding(end = 14.dp))
                }
            }
        }
    }
}

@Composable
fun RankedRows(items: List<Ranked>, colors: List<Color>, label: (String) -> String = { it }) {
    if (items.isEmpty()) {
        Text("Nothing in the last 30 days", color = TP.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = items.maxOf { it.cost }.coerceAtLeast(0.01)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { i, item ->
            val c = colors[i % colors.size]
            val frac by animateFloatAsState((item.cost / max).toFloat(), tween(900 + i * 120, easing = FastOutSlowInEasing), label = "rank")
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(c)
                    Spacer(Modifier.width(10.dp))
                    Text(label(item.name), style = MaterialTheme.typography.bodyMedium, color = TP.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.tokens(item.tokens), style = MaterialTheme.typography.bodySmall, color = TP.faint)
                    Spacer(Modifier.width(12.dp))
                    Text(Fmt.money(item.cost), style = MaterialTheme.typography.bodyMedium, color = TP.text)
                }
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(TP.track)) {
                    Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                }
            }
        }
    }
}
