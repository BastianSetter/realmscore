package de.morzo.realmscore.ui.tabs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.ProfileColors

private sealed interface ProfileDialog {
    data class Rename(val row: ProfileRow) : ProfileDialog
    data class ChangeColor(val row: ProfileRow) : ProfileDialog
    data class ArchiveConfirm(val row: ProfileRow) : ProfileDialog
    /** Verschmilzt [source] in ein auszuwählendes (aktives) Zielprofil. */
    data class MergeTargetPick(val source: ProfileRow) : ProfileDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileManagementViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }

    // Gültige Merge-Ziele: aktive Profile + Owner (geräteunabhängig), nie das Quellprofil selbst.
    val mergeTargets = remember(state.owner, state.active) {
        (listOfNotNull(state.owner) + state.active)
    }
    val canMerge = mergeTargets.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_management_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // --- Owner ---
            state.owner?.let { owner ->
                item(key = "owner_" + owner.profile.id) {
                    OwnerProfileCard(
                        row = owner,
                        onRename = { dialog = ProfileDialog.Rename(owner) },
                        onChangeColor = { dialog = ProfileDialog.ChangeColor(owner) },
                    )
                }
            }

            // --- Aktiv ---
            if (state.isLoaded && state.active.isEmpty() && state.owner == null) {
                item {
                    Text(
                        text = stringResource(R.string.profile_management_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.active.isNotEmpty()) {
                item("active_header") {
                    SectionHeader(stringResource(R.string.profile_management_active_section))
                }
            }
            items(state.active, key = { it.profile.id }) { row ->
                ActiveProfileCard(
                    row = row,
                    canMerge = mergeTargets.any { it.profile.id != row.profile.id },
                    onRename = { dialog = ProfileDialog.Rename(row) },
                    onChangeColor = { dialog = ProfileDialog.ChangeColor(row) },
                    onArchive = { dialog = ProfileDialog.ArchiveConfirm(row) },
                    onMerge = { dialog = ProfileDialog.MergeTargetPick(row) },
                )
            }

            // --- Merged ---
            if (state.merged.isNotEmpty()) {
                item("merged_header") {
                    SectionHeader(stringResource(R.string.profile_management_merged_section))
                }
                items(state.merged, key = { "merged_" + it.profile.id }) { row ->
                    MergedProfileCard(
                        row = row,
                        onUnmerge = { viewModel.unmerge(row.profile.id) },
                    )
                }
            }

            // --- Archiviert (einklappbar) ---
            if (state.archived.isNotEmpty()) {
                item("archived_header") {
                    ArchivedSectionHeader(
                        count = state.archived.size,
                        expanded = archivedExpanded,
                        onToggle = { archivedExpanded = !archivedExpanded },
                    )
                }
                if (archivedExpanded) {
                    items(state.archived, key = { "arch_" + it.profile.id }) { row ->
                        ArchivedProfileCard(
                            row = row,
                            canMerge = canMerge,
                            onUnarchive = { viewModel.unarchive(row.profile.id) },
                            onMerge = { dialog = ProfileDialog.MergeTargetPick(row) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is ProfileDialog.Rename -> RenameDialog(
            row = d.row,
            viewModel = viewModel,
            onDismiss = { dialog = null },
        )
        is ProfileDialog.ChangeColor -> ColorPickerDialog(
            currentColor = d.row.profile.colorArgb,
            onColorSelected = { color ->
                viewModel.changeColor(d.row.profile.id, color)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is ProfileDialog.ArchiveConfirm -> ArchiveConfirmDialog(
            row = d.row,
            onConfirm = {
                viewModel.archive(d.row.profile.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is ProfileDialog.MergeTargetPick -> MergeTargetPickDialog(
            source = d.source,
            candidates = mergeTargets.filter { it.profile.id != d.source.profile.id },
            onPick = { target ->
                viewModel.setMergeTarget(d.source.profile.id, target.profile.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileAvatar(profile: Profile, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = Color(profile.colorArgb), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.name.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun OwnerProfileCard(
    row: ProfileRow,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(row.profile)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_owner_label) +
                        " · " + stringResource(R.string.profile_games_count, row.gameCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.profile_actions_menu),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_action_rename)) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_action_change_color)) },
                        onClick = { menuExpanded = false; onChangeColor() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(
    row: ProfileRow,
    canMerge: Boolean,
    onRename: () -> Unit,
    onChangeColor: () -> Unit,
    onArchive: () -> Unit,
    onMerge: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(row.profile)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_games_count, row.gameCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.profile_actions_menu),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_action_rename)) },
                        onClick = { menuExpanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_action_change_color)) },
                        onClick = { menuExpanded = false; onChangeColor() },
                    )
                    if (canMerge) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_action_merge)) },
                            onClick = { menuExpanded = false; onMerge() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_action_archive)) },
                        onClick = { menuExpanded = false; onArchive() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MergedProfileCard(
    row: ProfileRow,
    onUnmerge: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(row.profile)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.mergeTargetName?.let {
                        stringResource(R.string.profile_merged_into, it)
                    } ?: stringResource(R.string.profile_merged_into_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUnmerge) {
                Text(stringResource(R.string.profile_action_unmerge))
            }
        }
    }
}

@Composable
private fun ArchivedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_management_archived_section) + " ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun ArchivedProfileCard(
    row: ProfileRow,
    canMerge: Boolean,
    onUnarchive: () -> Unit,
    onMerge: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(row.profile)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.profile_games_count, row.gameCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUnarchive) {
                Text(stringResource(R.string.profile_action_unarchive))
            }
            // Archivierte Profile dürfen nur reaktiviert oder gemergt werden.
            if (canMerge) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.profile_actions_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_action_merge)) },
                            onClick = { menuExpanded = false; onMerge() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    row: ProfileRow,
    viewModel: ProfileManagementViewModel,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(row.profile.name) }
    var error by remember { mutableStateOf<RenameResult?>(null) }
    val errorText = when (error) {
        RenameResult.EMPTY -> stringResource(R.string.profile_rename_error_empty)
        RenameResult.ERROR -> stringResource(R.string.state_error_default_title)
        RenameResult.SUCCESS, null -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.profile_rename_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.rename(row.profile, name) { result ->
                        if (result == RenameResult.SUCCESS) onDismiss() else error = result
                    }
                },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.profile_rename_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_dialog_cancel))
            }
        },
    )
}

@Composable
private fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_color_picker_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ProfileColors.PALETTE) { color ->
                    ColorSwatch(
                        color = color,
                        isSelected = color == currentColor,
                        onClick = { onColorSelected(color) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun ColorSwatch(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color = Color(color), shape = CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ArchiveConfirmDialog(
    row: ProfileRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_archive_confirm_title)) },
        text = { Text(stringResource(R.string.profile_archive_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.profile_archive_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_dialog_cancel))
            }
        },
    )
}

@Composable
private fun MergeTargetPickDialog(
    source: ProfileRow,
    candidates: List<ProfileRow>,
    onPick: (ProfileRow) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_merge_title)) },
        text = {
            Column {
                Text(stringResource(R.string.profile_merge_pick_target, source.profile.name))
                Spacer(Modifier.height(12.dp))
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_merge_no_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    candidates.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(row) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(row.profile, size = 32)
                            Spacer(Modifier.width(12.dp))
                            Text(row.profile.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_dialog_cancel))
            }
        },
    )
}
