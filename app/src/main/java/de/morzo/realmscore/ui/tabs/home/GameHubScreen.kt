package de.morzo.realmscore.ui.tabs.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.morzo.realmscore.R

/**
 * Root of the Game footer section. Hosts the same "new game / continue game" entry points as the
 * Start screen, but navigates in-section. Reuses [HomeViewModel] for the open-games list.
 */
@Composable
fun GameHubScreen(
    viewModel: HomeViewModel,
    onNewGame: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_game),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            GameEntryPoints(
                openGames = state.openGames,
                onNewGame = onNewGame,
                onOpenGame = onOpenGame,
            )
        }
    }
}
