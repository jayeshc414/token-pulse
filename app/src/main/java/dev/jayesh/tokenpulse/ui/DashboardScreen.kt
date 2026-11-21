package dev.jayesh.tokenpulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jayesh.tokenpulse.data.Fmt
import dev.jayesh.tokenpulse.data.Snapshot
import dev.jayesh.tokenpulse.data.SourceMode
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: UiState, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1000)
            value = Instant.now()
        }
    }
    val s = state.snapshot

    Box(
        Modifier
            .fillMaxSize()
            .background(TP.bg)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(TP.accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.08f),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.5f, size.height * 0.08f),
                )
            },
    ) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                item { Header(s, state, now, onRefresh, onSettings) }
                item { Banners(s, state, now, onSettings) }
                if (s != null) {
                    item { Reveal(0) { Hero(s, now) } }
                    if (s.plan?.fiveHour != null || s.plan?.sevenDay != null) item { Reveal(1) { Limits(s, now) } }
                    item { Reveal(2) { Stats(s, now) } }
                    item {
                        Reveal(3) {
                            Card {
                                SectionLabel("Daily spend", "14 days")
                                DailyBars(s.daily)
                            }
                        }
                    }
                    item {
                        Reveal(4) {
                            Card {
                                SectionLabel("Token mix · today", Fmt.tokens(s.today.tokens))
                                TokenMix(s.today)
                            }
                        }
                    }
                    item {
                        Reveal(5) {
                            Card {
                                SectionLabel("Models", "30 days")
                                RankedRows(s.models, listOf(TP.accent, TP.amber, TP.sky, TP.sage), Fmt::model)
                            }
                        }
                    }
                    item {
                        Reveal(6) {
                            Card {
                                SectionLabel("Projects", "30 days")
                                RankedRows(s.projects, listOf(TP.sage, TP.sky, TP.amber, TP.accent))
                            }
                        }
                    }
                    item {
                        Text(
                            "Costs are API-list-price equivalents computed from your local Claude Code logs. " +
                                "On a subscription you aren't billed these amounts — the session and weekly limits are what count.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TP.faint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item { Empty(onSettings) }
                }
            }
        }
    }
}

@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        shown = true
    }
    AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 6 }) { content() }
}

@Composable
private fun Header(s: Snapshot?, state: UiState, now: Instant, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Claude Code", style = MaterialTheme.typography.headlineMedium, color = TP.text)
                Spacer(Modifier.width(10.dp))
                Chip(if (state.mode == SourceMode.DEMO) "Demo" else Fmt.tier(s?.plan?.tier).removeSuffix(" plan"))
            }
            Spacer(Modifier.height(4.dp))
            val live = s != null && Duration.between(s.generatedAt, now).toMinutes() < 30
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (live) TP.sage else TP.faint, 7.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (s == null) "Not connected" else "Synced ${Fmt.ago(s.generatedAt, now)} · ${s.host}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TP.muted,
                )
            }
        }
        RoundIcon(onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = TP.text) }
        Spacer(Modifier.width(8.dp))
        RoundIcon(onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = TP.text) }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(TP.surfaceHi)
            .border(1.dp, TP.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun Chip(text: String, color: Color = TP.accent) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun Banners(s: Snapshot?, state: UiState, now: Instant, onSettings: () -> Unit) {
    val msg: Pair<String, Color>? = when {
        state.mode == SourceMode.DEMO -> "You're viewing demo data. Tap to connect your computer." to TP.amber
        state.error != null -> (state.error ?: "") to TP.danger
        s != null && Duration.between(s.generatedAt, now).toMinutes() >= 30 ->
            "Snapshot is ${Fmt.ago(s.generatedAt, now)}. Is the sync script running on ${s.host}?" to TP.amber
        s?.planError != null -> (s?.planError ?: "") to TP.muted
        else -> null
    }
    if (msg == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(msg.second.copy(alpha = 0.10f))
            .border(1.dp, msg.second.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .clickable(onClick = onSettings)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(msg.second)
        Spacer(Modifier.width(10.dp))
        Text(msg.first, style = MaterialTheme.typography.bodySmall, color = TP.text)
    }
}

@Composable
private fun Hero(s: Snapshot, now: Instant) {
    val fh = s.plan?.fiveHour
    val week = s.plan?.sevenDay
    val block = s.block?.takeIf { it.end.isAfter(now) }

    Card {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (fh != null) {
                DualRing(fh.pct, week?.pct, Modifier.size(250.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SESSION", style = MaterialTheme.typography.labelSmall, color = TP.muted)
                        Text(Fmt.pct(fh.pct), style = MaterialTheme.typography.displayLarge, color = TP.text)
                        Text(countdown(fh.resetsAt, now), style = MaterialTheme.typography.bodyMedium, color = TP.accentHi)
                        Text("until reset", style = MaterialTheme.typography.bodySmall, color = TP.faint)
                    }
                }
            } else {
                val pct = block?.let {
                    100.0 * Duration.between(it.start, now).toMillis() / Duration.between(it.start, it.end).toMillis()
                } ?: 0.0
                DualRing(pct, null, Modifier.size(250.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (block != null) "5H BLOCK" else "TODAY", style = MaterialTheme.typography.labelSmall, color = TP.muted)
                        Text(Fmt.money(block?.totals?.cost ?: s.today.cost), style = MaterialTheme.typography.displayLarge, color = TP.text)
                        if (block != null) {
                            Text(countdown(block.end, now), style = MaterialTheme.typography.bodyMedium, color = TP.accentHi)
                            Text("left in block", style = MaterialTheme.typography.bodySmall, color = TP.faint)
                        } else {
                            Text("no active block", style = MaterialTheme.typography.bodySmall, color = TP.faint)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (fh != null) {
                Legend(TP.accent, "Session", Fmt.pct(fh.pct))
                Legend(TP.amber, "Weekly", week?.let { Fmt.pct(it.pct) } ?: "—")
            } else {
                Legend(TP.accent, "Today", Fmt.money(s.today.cost))
                Legend(TP.amber, "Burn", block?.let { Fmt.money(it.burnRatePerHour) + "/h" } ?: "—")
            }
            Legend(TP.sky, "Tokens today", Fmt.tokens(s.today.tokens))
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = TP.muted)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = TP.text)
    }
}

private fun countdown(t: Instant?, now: Instant): String {
    if (t == null) return "—"
    val d = Duration.between(now, t)
    if (d.isNegative) return "0:00:00"
    return String.format(java.util.Locale.US, "%d:%02d:%02d", d.toHours(), d.toMinutes() % 60, d.seconds % 60)
}

@Composable
private fun Limits(s: Snapshot, now: Instant) {
    val p = s.plan ?: return
    Card {
        SectionLabel("Plan limits", Fmt.tier(p.tier))
        p.fiveHour?.let { LimitRow("Current session", it, TP.accent, TP.accentHi, "Resets in ${Fmt.until(it.resetsAt, now)}") }
        p.sevenDay?.let { LimitRow("Weekly · all models", it, TP.amber, TP.amberHi, "Resets ${Fmt.weekdayAt(it.resetsAt)}") }
        p.sevenDayOpus?.let { LimitRow("Weekly · Opus", it, TP.sky, Color(0xFFB4CBF5), "Resets ${Fmt.weekdayAt(it.resetsAt)}") }
        p.sevenDaySonnet?.let { LimitRow("Weekly · Sonnet", it, TP.sage, Color(0xFFBFE0C6), "Resets ${Fmt.weekdayAt(it.resetsAt)}") }
        p.extraPct?.let {
            LimitRow("Extra usage", dev.jayesh.tokenpulse.data.LimitWindow(it, null), TP.danger, Color(0xFFFF9A8F), "Monthly extra-usage credit")
        }
    }
}

@Composable
private fun Stats(s: Snapshot, now: Instant) {
    val b = s.block?.takeIf { it.end.isAfter(now) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Today", Fmt.money(s.today.cost), "${s.today.messages} replies · ${s.sessionsToday} sessions", TP.accent, Modifier.weight(1f))
            StatTile("This month", Fmt.money(s.month.cost), "${Fmt.tokens(s.month.tokens)} tokens", TP.amber, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                "Burn rate",
                b?.let { Fmt.money(it.burnRatePerHour) + "/h" } ?: "—",
                b?.let { "${Fmt.tokens(it.tokensPerMinute)} tok/min" } ?: "No active block",
                TP.sky, Modifier.weight(1f),
            )
            StatTile(
                "Block forecast",
                b?.let { Fmt.money(it.projectedCost) } ?: "—",
                b?.let { "ends ${Fmt.clock(it.end)}" } ?: "Starts with your next prompt",
                TP.sage, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Empty(onSettings: () -> Unit) {
    Card(Modifier.clickable(onClick = onSettings)) {
        Text("Connect your computer", style = MaterialTheme.typography.titleMedium, color = TP.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Run the Token Pulse sync script where you use Claude Code, then paste its Gist ID in Settings.",
            style = MaterialTheme.typography.bodyMedium, color = TP.muted,
        )
    }
}
