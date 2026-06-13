package de.morzo.realmscore.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.AppLanguage

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOwnerCreated: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val canSubmit = state.name.trim().isNotEmpty() && !state.isSubmitting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_headline),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        LanguageSelector(
            selected = state.language,
            onSelect = viewModel::onLanguageSelected,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            enabled = !state.isSubmitting,
            isError = state.error != null,
        )
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_error_empty),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.onContinue(onOwnerCreated) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.onboarding_language_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.entries.forEach { lang ->
                val labelRes = when (lang) {
                    AppLanguage.SYSTEM -> R.string.settings_language_system
                    AppLanguage.GERMAN -> R.string.settings_language_german
                    AppLanguage.ENGLISH -> R.string.settings_language_english
                }
                val label = stringResource(labelRes)
                val content: @Composable () -> Unit = {
                    Text("${lang.flagEmoji}  $label")
                }
                if (lang == selected) {
                    Button(onClick = { onSelect(lang) }, content = { content() })
                } else {
                    OutlinedButton(onClick = { onSelect(lang) }, content = { content() })
                }
            }
        }
    }
}
