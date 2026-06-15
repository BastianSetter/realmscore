package de.morzo.realmscore.ui.tabs.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.ui.util.formatRelativeDate
import de.morzo.realmscore.ui.util.formatShortDate

/**
 * Shared "start a game / continue a game" entry points, rendered both on the Start screen
 * ([HomeScreen]) and as the root of the Game footer section ([GameHubScreen]). Callers supply the
 * navigation lambdas so each surface can apply the right NavOptions (Start crosses into the Game
 * section; the hub navigates in-section).
 */
@Composable
fun GameEntryPoints(
    openGames: List<OpenGameCard>,
    onNewGame: () -> Unit,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.home_new_game))
        }
        if (openGames.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_games_to_continue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OpenGamesRow(games = openGames, onOpen = onOpenGame)
        }
    }
}

@Composable
private fun OpenGamesRow(
    games: List<OpenGameCard>,
    onOpen: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.gameId }) { game ->
            OpenGameCardItem(game = game, onClick = { onOpen(game.gameId) })
        }
    }
}

@Composable
private fun OpenGameCardItem(game: OpenGameCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(260.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = game.displayName
                    ?: stringResource(
                        R.string.home_open_game_fallback_name,
                        formatShortDate(game.startedAt),
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelativeDate(game.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AvatarRow(participants = game.participants, maxVisible = 5)
            if (game.topStand.profileId != null && game.topStand.name != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_open_game_top_stand,
                        game.topStand.name,
                        game.topStand.score,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AvatarRow(participants: List<ParticipantBadge>, maxVisible: Int) {
    if (participants.isEmpty()) return
    val visible = participants.take(maxVisible)
    val overflow = participants.size - visible.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        visible.forEach { p ->
            PlayerDot(colorArgb = p.colorArgb, size = 22.dp, initial = p.name.take(1).uppercase())
            Spacer(Modifier.width(4.dp))
        }
        if (overflow > 0) {
            Text(
                text = stringResource(R.string.home_avatar_overflow, overflow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerDot(
    colorArgb: Int,
    size: Dp,
    initial: String? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color = Color(colorArgb), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
