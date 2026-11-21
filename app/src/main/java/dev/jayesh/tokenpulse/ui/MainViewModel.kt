package dev.jayesh.tokenpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jayesh.tokenpulse.data.Prefs
import dev.jayesh.tokenpulse.data.Repository
import dev.jayesh.tokenpulse.data.Snapshot
import dev.jayesh.tokenpulse.data.SourceMode
import dev.jayesh.tokenpulse.widget.Widgets
import dev.jayesh.tokenpulse.work.Alerts
import dev.jayesh.tokenpulse.work.Sync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val snapshot: Snapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val mode: SourceMode = SourceMode.DEMO,
)

data class SettingsForm(
    val mode: SourceMode,
    val gistId: String,
    val token: String,
    val url: String,
    val refreshMinutes: Int,
    val alerts: Boolean,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val repo = Repository(app)
    private val _state = MutableStateFlow(UiState(snapshot = repo.cached(), mode = prefs.mode, error = prefs.lastError))
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val r = repo.refresh()
            val ctx = getApplication<Application>()
            Widgets.updateAll(ctx)
            r.getOrNull()?.let { Alerts.check(ctx, it) }
            _state.update {
                it.copy(
                    snapshot = r.getOrNull() ?: repo.cached(),
                    refreshing = false,
                    error = prefs.lastError,
                    mode = prefs.mode,
                )
            }
        }
    }

    fun form() = SettingsForm(prefs.mode, prefs.gistId, prefs.githubToken, prefs.customUrl, prefs.refreshMinutes, prefs.alertsEnabled)

    fun save(f: SettingsForm) {
        val modeChanged = f.mode != prefs.mode || f.gistId != prefs.gistId || f.url != prefs.customUrl
        prefs.mode = f.mode
        prefs.gistId = f.gistId
        prefs.githubToken = f.token
        prefs.customUrl = f.url
        prefs.refreshMinutes = f.refreshMinutes
        prefs.alertsEnabled = f.alerts
        if (modeChanged) prefs.cachedJson = null
        Sync.schedule(getApplication())
        _state.update { it.copy(snapshot = repo.cached(), mode = f.mode, error = null) }
        refresh()
    }
}
