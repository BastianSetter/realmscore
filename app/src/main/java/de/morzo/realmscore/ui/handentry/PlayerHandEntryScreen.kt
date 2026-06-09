package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHandEntryScreen(
    container: AppContainer,
    roundId: String,
    profileId: String,
    onSubmitDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: PlayerHandEntryViewModel = viewModel(
        factory = PlayerHandEntryViewModel.Factory(
            cardLookup = container.cardLookup,
            handCardRepo = container.handCardRepository,
            profileRepo = container.profileRepository,
            engine = container.scoringEngine,
            optimalSolver = container.optimalSolver,
            roundId = roundId,
            profileId = profileId,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isLoading) stringResource(R.string.round_entry_title_loading)
                        else stringResource(R.string.player_hand_title, state.playerName),
                    )
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
            return@Scaffold
        }

        PlayerHandCaptureContent(
            modifier = Modifier.padding(padding),
            state = state,
            allCards = vm.allCards,
            necromancerCandidates = { placedKeys ->
                container.cardLookup.getNecromancerCandidates(handKeys = placedKeys)
            },
            onSetCardInSlot = vm::setCardInSlot,
            onClearSlot = vm::clearSlot,
            onSetJokerAssignment = vm::setJokerAssignment,
            onApplyOptimal = vm::applyOptimal,
            onSetNecromancerPick = vm::setNecromancerPick,
            onClearNecromancerPick = vm::clearNecromancerPick,
            onSubmit = { vm.submit(onSubmitDone) },
            submitLabel = stringResource(R.string.player_hand_submit),
        )
    }
}
