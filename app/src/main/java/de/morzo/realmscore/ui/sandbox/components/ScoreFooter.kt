package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R

@Composable
fun ScoreFooter(
    score: Int,
    onBreakdownClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sandbox_score, score),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (onBreakdownClick != null) {
                TextButton(onClick = onBreakdownClick) {
                    Text(stringResource(R.string.sandbox_show_ring))
                }
            }
        }
    }
}
