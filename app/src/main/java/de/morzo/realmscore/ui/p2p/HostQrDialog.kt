package de.morzo.realmscore.ui.p2p

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.data.p2p.QrCodeHelper
import de.morzo.realmscore.data.p2p.SyncProtocol
import de.morzo.realmscore.domain.p2p.model.HandshakePayload

private const val QR_SIZE_PX = 512

/**
 * Re-displays the host's session QR + 6-digit code mid-game (Phase 28, §6 #2). Shown from the Home
 * card while [SessionState.Hosting] so a client whose silent rejoin failed can re-scan and reconnect.
 * Reads the live [payload] / [sessionCode] from the still-open host session — nothing persisted.
 */
@Composable
fun HostQrDialog(
    payload: HandshakePayload,
    sessionCode: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.p2p_reconciliation_done))
            }
        },
        title = { Text(stringResource(R.string.p2p_show_qr)) },
        text = {
            val qr: Bitmap = remember(payload) {
                QrCodeHelper.generate(SyncProtocol.encodeHandshake(payload), QR_SIZE_PX)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.p2p_scan_qr),
                    modifier = Modifier.size(220.dp),
                )
                Text(
                    stringResource(R.string.p2p_session_code_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(sessionCode, style = MaterialTheme.typography.headlineMedium)
            }
        },
    )
}
