package de.morzo.realmscore.ui.newgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.Profile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(
    container: AppContainer,
    onGameStarted: (gameId: String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: NewGameViewModel = viewModel(
        factory = NewGameViewModel.Factory(
            profileRepo = container.profileRepository,
            gameRepo = container.gameRepository,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_game_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.new_game_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ModeSection(state.mode, onModeChange = vm::setMode)
            Spacer(Modifier.height(16.dp))
            TargetSection(state.mode, state.targetValue, onChange = vm::setTarget)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.new_game_players_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            PlayersList(
                participants = state.participants,
                onRemove = vm::removeParticipant,
            )
            Spacer(Modifier.height(12.dp))

            AddPlayerField(
                query = state.addQuery,
                suggestions = state.suggestions,
                error = state.addError,
                onQueryChange = vm::onQueryChange,
                onAddExisting = vm::addExistingProfile,
                onAddNew = { vm.addNewProfile(state.addQuery) },
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { vm.startGame(onGameStarted) },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.new_game_start))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeSection(
    mode: GameMode,
    onModeChange: (GameMode) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.new_game_mode_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        ModeRow(
            selected = mode == GameMode.FIXED_ROUNDS,
            label = stringResource(R.string.new_game_mode_fixed_rounds),
            onClick = { onModeChange(GameMode.FIXED_ROUNDS) },
        )
        ModeRow(
            selected = mode == GameMode.POINT_LIMIT,
            label = stringResource(R.string.new_game_mode_point_limit),
            onClick = { onModeChange(GameMode.POINT_LIMIT) },
        )
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun TargetSection(
    mode: GameMode,
    value: Int,
    onChange: (Int) -> Unit,
) {
    val labelRes = when (mode) {
        GameMode.FIXED_ROUNDS -> R.string.new_game_target_rounds
        GameMode.POINT_LIMIT -> R.string.new_game_target_points
    }
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            val parsed = digits.toIntOrNull() ?: 0
            onChange(parsed)
        },
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlayersList(
    participants: List<ParticipantRow>,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        participants.forEach { row ->
            ParticipantItem(row = row, onRemove = onRemove)
        }
    }
}

@Composable
private fun ParticipantItem(
    row: ParticipantRow,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color = Color(row.colorArgb), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge)
            if (row.isOwner) {
                Text(
                    text = stringResource(R.string.new_game_owner_badge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!row.isOwner) {
            IconButton(onClick = { onRemove(row.profileId) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.new_game_remove_button),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddPlayerField(
    query: String,
    suggestions: List<Profile>,
    error: AddError?,
    onQueryChange: (String) -> Unit,
    onAddExisting: (Profile) -> Unit,
    onAddNew: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.new_game_add_player_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    // Pull the field above the keyboard when it gains focus so neither
                    // the field nor the "start game" button below stays hidden.
                    if (it.isFocused) {
                        scope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                },
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAddNew() }),
            trailingIcon = {
                if (query.isNotBlank()) {
                    Button(
                        onClick = onAddNew,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.new_game_add_button))
                    }
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            val msg = when (error) {
                AddError.EMPTY_NAME -> R.string.new_game_error_name_empty
                AddError.NAME_EXISTS -> R.string.new_game_error_name_exists
                AddError.MAX_PLAYERS_REACHED -> R.string.new_game_error_max_players
            }
            Text(
                text = stringResource(msg),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (focused && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
            ) {
                suggestions.forEach { profile ->
                    SuggestionRow(profile = profile, onClick = { onAddExisting(profile) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(profile: Profile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color = Color(profile.colorArgb), shape = CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
    }
}
