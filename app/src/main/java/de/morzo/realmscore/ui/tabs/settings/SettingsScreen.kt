package de.morzo.realmscore.ui.tabs.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onChangeUsername: () -> Unit,
    onManageProfiles: () -> Unit,
    onAppReset: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showClearGameDataDialog by remember { mutableStateOf(false) }
    var showResetAppDialog by remember { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // String templates resolved here so the event collector can format messages without a Composable.
    val importResultTemplate = stringResource(R.string.backup_import_result)
    val importMissingProfileTemplate = stringResource(R.string.backup_import_missing_profile)
    val exportErrorMsg = stringResource(R.string.backup_export_error)
    val importSchemaErrorMsg = stringResource(R.string.backup_import_error_schema)
    val importInvalidMsg = stringResource(R.string.backup_import_error_invalid)
    val shareTitle = stringResource(R.string.backup_share_title)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.ShareFile -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, shareTitle)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                BackupEvent.ExportFailed ->
                    snackbarHostState.showSnackbar(exportErrorMsg)
                is BackupEvent.ImportSucceeded -> {
                    val r = event.result
                    val base = String.format(
                        importResultTemplate,
                        r.gamesCreated,
                        r.roundsAdded,
                        r.roundsSkipped,
                    )
                    val message = if (r.roundsSkippedMissingProfile > 0) {
                        base + " " + String.format(
                            importMissingProfileTemplate,
                            r.roundsSkippedMissingProfile,
                        )
                    } else {
                        base
                    }
                    snackbarHostState.showSnackbar(message)
                }
                BackupEvent.ImportSchemaTooNew ->
                    snackbarHostState.showSnackbar(importSchemaErrorMsg)
                BackupEvent.ImportInvalid ->
                    snackbarHostState.showSnackbar(importInvalidMsg)
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) pendingImportUri = uri.toString()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { SectionHeader(stringResource(R.string.settings_profile)) }
        item {
            ProfileSection(
                owner = state.ownerProfile,
                onChangeUsername = onChangeUsername,
                onManageProfiles = onManageProfiles,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_game)) }
        item {
            IntField(
                label = stringResource(R.string.settings_default_point_limit),
                value = state.defaultPointLimit,
                onValueChange = viewModel::setDefaultPointLimit,
            )
        }
        item {
            IntField(
                label = stringResource(R.string.settings_default_round_count),
                value = state.defaultRoundCount,
                onValueChange = viewModel::setDefaultRoundCount,
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_discard_capture_enabled),
                checked = state.discardCaptureEnabled,
                onCheckedChange = viewModel::setDiscardCaptureEnabled,
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_picker_search_enabled),
                checked = state.pickerSearchEnabled,
                onCheckedChange = viewModel::setPickerSearchEnabled,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_appearance)) }
        item {
            LanguageRadioGroup(
                selected = state.appLanguage,
                onSelect = viewModel::setAppLanguage,
            )
        }
        item {
            ThemeModeRadioGroup(
                selected = state.themeMode,
                onSelect = viewModel::setThemeMode,
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_dynamic_colors),
                checked = state.useDynamicColors,
                onCheckedChange = viewModel::setUseDynamicColors,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_data)) }
        item {
            DataSection(
                info = state.dataInfo,
                onExportBackup = { viewModel.exportBackup(context) },
                onImportBackup = { importPicker.launch("application/json") },
                onClearGameData = { showClearGameDataDialog = true },
                onResetApp = { showResetAppDialog = true },
            )
        }

        item { SectionHeader(stringResource(R.string.settings_about)) }
        item { AboutSection() }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    pendingImportUri?.let { uriString ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    viewModel.importBackup(context, Uri.parse(uriString))
                }) {
                    Text(stringResource(R.string.backup_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (showClearGameDataDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_confirm_clear_data_title),
            body = stringResource(R.string.settings_confirm_clear_data_body),
            onDismiss = { showClearGameDataDialog = false },
            onConfirmed = {
                showClearGameDataDialog = false
                viewModel.clearGameData()
            },
        )
    }
    if (showResetAppDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_confirm_reset_app_title),
            body = stringResource(R.string.settings_confirm_reset_app_body),
            onDismiss = { showResetAppDialog = false },
            onConfirmed = {
                showResetAppDialog = false
                viewModel.resetApp { onAppReset() }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProfileSection(
    owner: Profile?,
    onChangeUsername: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (owner != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = Color(owner.colorArgb), shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = owner.name.take(1).uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = owner.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_profile_owner_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(stringResource(R.string.settings_profile_none))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onChangeUsername,
                enabled = owner != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_change_username))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManageProfiles,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_manage_profiles))
            }
        }
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(6)
            text = filtered
            filtered.toIntOrNull()?.let { onValueChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguageRadioGroup(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    val options = listOf(
        AppLanguage.SYSTEM to R.string.settings_language_system,
        AppLanguage.GERMAN to R.string.settings_language_german,
        AppLanguage.ENGLISH to R.string.settings_language_english,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        options.forEach { (lang, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (lang == selected),
                        onClick = { onSelect(lang) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = (lang == selected),
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun ThemeModeRadioGroup(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        options.forEach { (mode, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (mode == selected),
                        onClick = { onSelect(mode) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = (mode == selected),
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun DataSection(
    info: DataInfo,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearGameData: () -> Unit,
    onResetApp: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_data_open_games, info.openGamesCount))
            Text(stringResource(R.string.settings_data_closed_games, info.closedGamesCount))
            Text(stringResource(R.string.settings_data_rounds, info.totalRoundsCount))
            Text(stringResource(R.string.settings_data_profiles, info.profilesCount))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_export_backup))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onImportBackup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_import_backup))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onClearGameData,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_clear_game_data))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetApp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_reset_app),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val confirmWord = stringResource(R.string.settings_confirm_keyword)
    val enabled = typed.trim() == confirmWord
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_confirm_type_keyword,
                                confirmWord,
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmed, enabled = enabled) {
                Text(
                    text = stringResource(R.string.settings_confirm_delete),
                    color = if (enabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
