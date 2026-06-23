package de.morzo.realmscore.ui.p2p

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R

/**
 * Shown on a joined phone after the host tapped "Neues Spiel starten" on the game-end screen (Phase 28).
 * The host is building the next game; the client waits here until the host starts the round, at which
 * point the central `OpenRound` nav signal pulls it straight into capture. [onLeave] is an escape hatch
 * (the host might abandon the setup) — leaving doesn't block the bring-along, since the round signal is
 * observed centrally regardless of the current screen.
 */
@Composable
fun NewGameWaitScreen(onLeave: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.p2p_new_game_wait_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = stringResource(R.string.p2p_new_game_wait_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = onLeave,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.game_summary_back_home))
            }
        }
    }
}
