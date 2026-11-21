package dev.jayesh.tokenpulse.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jayesh.tokenpulse.data.SourceMode

@Composable
fun SettingsScreen(initial: SettingsForm, onBack: () -> Unit, onSave: (SettingsForm) -> Unit) {
    var mode by remember { mutableStateOf(initial.mode) }
    var gist by remember { mutableStateOf(initial.gistId) }
    var token by remember { mutableStateOf(initial.token) }
    var url by remember { mutableStateOf(initial.url) }
    var minutes by remember { mutableStateOf(initial.refreshMinutes) }
    var alerts by remember { mutableStateOf(initial.alerts) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        alerts = granted
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TP.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(TP.surfaceHi).border(1.dp, TP.border, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TP.text) }
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TP.text)
        }

        Card {
            SectionLabel("Data source")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("GitHub Gist", mode == SourceMode.GIST, Modifier.weight(1f)) { mode = SourceMode.GIST }
                Pill("URL", mode == SourceMode.URL, Modifier.weight(1f)) { mode = SourceMode.URL }
                Pill("Demo", mode == SourceMode.DEMO, Modifier.weight(1f)) { mode = SourceMode.DEMO }
            }
            Spacer(Modifier.height(16.dp))
            when (mode) {
                SourceMode.GIST -> {
                    Field("Gist ID", gist, "e.g. 3f9c1a…  (printed by setup)") { gist = it }
                    Spacer(Modifier.height(10.dp))
                    Field("GitHub token (optional)", token, "Same 'gist' token — lifts the 60/hour limit", secret = true) { token = it }
                }
                SourceMode.URL -> Field("Snapshot URL", url, "Any URL serving claude-usage.json") { url = it }
                SourceMode.DEMO -> Text(
                    "Sample data so you can try the app and widgets before connecting.",
                    style = MaterialTheme.typography.bodyMedium, color = TP.muted,
                )
            }
        }

        Card {
            SectionLabel("Background refresh")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    Pill("$m min", minutes == m, Modifier.weight(1f)) { minutes = m }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Android runs background work at most every 15 minutes. Widgets also have a refresh button, and the reset countdown ticks live.",
                style = MaterialTheme.typography.bodySmall, color = TP.faint,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Limit alerts", style = MaterialTheme.typography.bodyLarge, color = TP.text)
                    Text("Session at 75% / 90%, weekly at 80% / 95%", style = MaterialTheme.typography.bodySmall, color = TP.muted)
                }
                Switch(
                    checked = alerts,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else alerts = on
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = TP.accent, checkedThumbColor = Color.White),
                )
            }
        }

        Button(
            onClick = { onSave(SettingsForm(mode, gist, token, url, minutes, alerts)) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TP.accent, contentColor = Color(0xFF1A0E08)),
        ) { Text("Save & sync", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }

        Card {
            SectionLabel("Connect your computer")
            Step(1, "Save claude-usage-sync.mjs on the computer where you run Claude Code (Node 18+).")
            Step(2, "Create a GitHub token with only the gist scope, then run:")
            Code("node claude-usage-sync.mjs setup")
            Step(3, "Paste the Gist ID it prints into the field above.")
            Step(4, "Keep it syncing (or schedule it every 5 minutes):")
            Code("node claude-usage-sync.mjs --watch")
            Spacer(Modifier.height(6.dp))
            Text(
                "The script reads ~/.claude/projects logs locally and, if you're signed in with a Claude plan, your session and weekly limits. Only the summary is uploaded to your secret gist — no prompts or code.",
                style = MaterialTheme.typography.bodySmall, color = TP.faint,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TP.accent.copy(alpha = 0.18f) else TP.bg)
            .border(1.dp, if (selected) TP.accent.copy(alpha = 0.6f) else TP.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) TP.accentHi else TP.muted)
    }
}

@Composable
private fun Field(label: String, value: String, hint: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(hint) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TP.accent,
            unfocusedBorderColor = TP.border,
            focusedLabelColor = TP.accentHi,
            cursorColor = TP.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(TP.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.bodySmall, color = TP.accentHi)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TP.text, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Code(text: String) {
    SelectionContainer {
        Text(
            text,
            fontFamily = Mono,
            fontSize = 13.sp,
            color = TP.amberHi,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, top = 2.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0A09))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
