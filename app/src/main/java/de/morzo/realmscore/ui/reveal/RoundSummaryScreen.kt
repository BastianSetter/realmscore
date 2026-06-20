package de.morzo.realmscore.ui.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.ui.util.currentLocale
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.components.HandBreakdownSheet
import de.morzo.realmscore.ui.components.suitColor
import de.morzo.realmscore.ui.components.suitOnColor
import de.morzo.realmscore.ui.sandbox.components.MoveToSandboxIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundSummaryScreen(
    container: AppContainer,
    roundId: String,
    onNextRound: (newRoundId: String) -> Unit,
    onCompleteGame: (gameId: String) -> Unit,
    onEditRound: () -> Unit,
    onShowRevealAgain: () -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: RoundSummaryViewModel = viewModel(
        factory = RoundSummaryViewModel.Factory(
            roundId = roundId,
            roundRepo = container.roundRepository,
            gameRepo = container.gameRepository,
            profileRepo = container.profileRepository,
            handCardRepo = container.handCardRepository,
            cardLookup = container.cardLookup,
            engine = container.scoringEngine,
            p2p = container.p2pSessionRepository,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var breakdownProfileId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.round_summary_title, state.roundNumber))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.game_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.players.forEach { player ->
                    PlayerSummaryCard(
                        player = player,
                        isWinner = player.profileId == state.winnerId,
                        onTap = { breakdownProfileId = player.profileId },
                        onMoveToSandbox = {
                            onMoveToSandbox(state.gameId, roundId, player.profileId)
                        },
                    )
                }

                if (state.discardScanned) {
                    Spacer(Modifier.height(8.dp))
                    DiscardSection(cards = state.discardCards)
                }

                Spacer(Modifier.height(16.dp))

                // P2P client: the host drives round advancement and game closing — the client only
                // follows (via the OpenRound signal / GameClosed), so these controls are hidden.
                if (state.canStartNextRound && !state.isP2pClient) {
                    Button(
                        onClick = { vm.startNextRound(onNextRound) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.round_summary_next_round))
                    }
                }

                if (!state.isP2pClient) {
                    OutlinedButton(
                        onClick = { onCompleteGame(state.gameId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.round_summary_complete_game))
                    }
                }

                if (state.canEditRound) {
                    TextButton(
                        onClick = onEditRound,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.round_summary_edit_round))
                    }
                }

                TextButton(
                    onClick = onShowRevealAgain,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.round_summary_reveal_again))
                }
            }
        }
    }

    val activeProfileId = breakdownProfileId
    if (activeProfileId != null) {
        val breakdownVm: BreakdownViewModel = viewModel(
            key = "breakdown-$roundId-$activeProfileId",
            factory = BreakdownViewModel.Factory(
                handCardRepo = container.handCardRepository,
                engine = container.scoringEngine,
                cardLookup = container.cardLookup,
                roundId = roundId,
                profileId = activeProfileId,
            ),
        )
        val result by breakdownVm.scoringResult.collectAsStateWithLifecycle()
        val handCards by breakdownVm.handCards.collectAsStateWithLifecycle()
        result?.let {
            HandBreakdownSheet(
                cards = handCards,
                result = it,
                cardLookup = container.cardLookup::getByKey,
                onDismiss = { breakdownProfileId = null },
            )
        }
    }
}

@Composable
private fun PlayerSummaryCard(
    player: PlayerSummary,
    isWinner: Boolean,
    onTap: () -> Unit,
    onMoveToSandbox: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isWinner) 2.dp else 1.dp,
                color = if (isWinner) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onTap)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = Color(player.colorArgb), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = player.name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isWinner) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = stringResource(R.string.round_summary_winner_badge),
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.round_summary_points, player.totalScore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = player.totalScore.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            MoveToSandboxIcon(onClick = onMoveToSandbox)
        }
        Spacer(Modifier.height(8.dp))
        MiniStackedBar(
            positive = player.positiveContribution,
            negative = player.negativeContribution,
            blankedCount = player.blankedCount,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscardSection(cards: List<CardDefinition>) {
    val locale = currentLocale()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.round_summary_discard_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.round_summary_discard_count, cards.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cards.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cards.forEach { card ->
                        Surface(
                            color = suitColor(card.suit),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = card.displayName(locale),
                                style = MaterialTheme.typography.labelMedium,
                                color = suitOnColor(card.suit),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStackedBar(positive: Int, negative: Int, blankedCount: Int) {
    val blankedWeight = blankedCount * 5
    val total = positive + negative + blankedWeight
    if (total <= 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(3.dp),
                ),
        )
        return
    }
    val posFraction = positive.toFloat() / total
    val negFraction = negative.toFloat() / total
    val blkFraction = blankedWeight.toFloat() / total

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (posFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(posFraction)
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
        if (negFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(negFraction)
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
        if (blkFraction > 0f) {
            Box(
                modifier = Modifier
                    .weight(blkFraction)
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}
