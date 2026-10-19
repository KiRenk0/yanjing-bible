package org.yanjing.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import org.yanjing.app.diagnostics.StartupRecovery
import kotlinx.coroutines.flow.first
import org.yanjing.app.ui.YanJingApp
import org.yanjing.app.ui.YanJingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Also cover existing desktop shortcuts pointing directly at this component.
        StartupRecovery(applicationContext).begin()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(model) {
                model.state.first { !it.loading }
                withFrameNanos { }
                withFrameNanos { }
                StartupRecovery(applicationContext).complete()
                model.updates.start()
            }
            YanJingTheme(state.appearance, state.dynamicColor) { YanJingApp(state, model) }
        }
    }
}
