package de.morzo.realmscore.ui.reveal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.ui.util.currentLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevealScreen(
    container: AppContainer,
    roundId: String,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val vm: RevealViewModel = viewModel(
        factory = RevealViewModel.Factory(
            roundId = roundId,
            roundRepo = container.roundRepository,
            gameRepo = container.gameRepository,
            profileRepo = container.profileRepository,
            handCardRepo = container.handCardRepository,
            cardLookup = container.cardLookup,
            engine = container.scoringEngine,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = !state.isLoading) {
                // Tapping the last (winning) player goes straight to the round summary; there is no
                // intermediate "continue" screen.
                val onLastReveal = state.players.isNotEmpty() &&
                    state.currentRevealIndex >= state.players.lastIndex
                if (onLastReveal) onDone() else vm.revealNext()
            },
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else if (state.players.isEmpty()) {
            Text(
                text = stringResource(R.string.round_entry_status_not_started),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            AnimatedContent(
                targetState = state.currentRevealIndex,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(animationSpec = tween(400))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(animationSpec = tween(300)))
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                label = "reveal-player",
            ) { index ->
                // currentRevealIndex never advances past the last player anymore (its tap calls
                // onDone), so only the player card is ever shown here.
                if (index < state.players.size) {
                    PlayerRevealCard(
                        player = state.players[index],
                        isWinner = index == state.players.size - 1,
                        cardLookup = container.cardLookup::getByKey,
                    )
                }
            }
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(R.string.round_summary_skip),
                color = Color.White,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerRevealCard(
    player: PlayerReveal,
    isWinner: Boolean,
    cardLookup: (String) -> de.morzo.realmscore.domain.model.CardDefinition?,
) {
    val animatedScore by animateIntAsState(
        targetValue = player.finalScore,
        animationSpec = tween(durationMillis = 1500),
        label = "score-count-up",
    )
    val crownScale by animateFloatAsState(
        targetValue = if (isWinner) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 800),
        label = "crown-scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isWinner) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = stringResource(R.string.reveal_winner_label),
                tint = Color(0xFFFFD54F),
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = crownScale
                        scaleY = crownScale
                    },
            )
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(color = Color(player.colorArgb), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = player.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Text(
            text = player.name,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.reveal_points, animatedScore),
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
        )

        if (player.topCardKeys.isNotEmpty()) {
            val locale = currentLocale()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                player.topCardKeys.forEach { key ->
                    TopCardChip(cardLookup(key)?.displayName(locale) ?: key)
                }
            }
        }
    }
}

@Composable
private fun TopCardChip(name: String) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
