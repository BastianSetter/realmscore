package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R

@Composable
fun MoveToSandboxIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.then(if (compact) Modifier.size(28.dp) else Modifier),
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = stringResource(R.string.move_to_sandbox),
            tint = MaterialTheme.colorScheme.primary,
            modifier = if (compact) Modifier.size(16.dp) else Modifier.size(20.dp),
        )
    }
}
