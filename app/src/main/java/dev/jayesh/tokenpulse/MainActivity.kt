package dev.jayesh.tokenpulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jayesh.tokenpulse.ui.DashboardScreen
import dev.jayesh.tokenpulse.ui.MainViewModel
import dev.jayesh.tokenpulse.ui.SettingsScreen
import dev.jayesh.tokenpulse.ui.TokenPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TokenPulseTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var settings by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = settings) { settings = false }

                AnimatedContent(settings, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { inSettings ->
                    if (inSettings) {
                        SettingsScreen(
                            initial = vm.form(),
                            onBack = { settings = false },
                            onSave = {
                                vm.save(it)
                                settings = false
                            },
                        )
                    } else {
                        DashboardScreen(state, onRefresh = vm::refresh, onSettings = { settings = true })
                    }
                }
            }
        }
    }
}
