package com.quantumff.segue

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quantumff.segue.core.designsystem.theme.SegueTheme
import com.quantumff.segue.core.playback.PlaybackIntents
import com.quantumff.segue.ui.SegueApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    /** Counts "open Now Playing" intents (from the widgets); each new value opens it once. */
    private val nowPlayingRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on recreation: the restored back stack already has Now Playing if it was open.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            when (val state = uiState) {
                // A few milliseconds while DataStore loads: no flash of the wrong theme or screen.
                MainActivityUiState.Loading -> SegueTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }

                is MainActivityUiState.Ready -> SegueTheme(config = state.theme) {
                    SegueApp(hasServer = state.hasServer, openNowPlayingRequest = nowPlayingRequests.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PlaybackIntents.ACTION_OPEN_NOW_PLAYING) nowPlayingRequests.intValue++
    }
}
