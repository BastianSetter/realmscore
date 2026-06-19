package de.morzo.realmscore.ui.p2p

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.data.p2p.BluetoothRfcommManager
import de.morzo.realmscore.data.p2p.QrCodeHelper
import de.morzo.realmscore.data.p2p.SyncProtocol
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.SessionState

private const val QR_SIZE_PX = 512

/**
 * Reusable host-side "open for P2P joins" section (Phase 28). Shows a button that requests
 * BLUETOOTH_ADVERTISE (API 31+) and the system discoverable dialog, then calls [onOpenForJoins].
 * Once a [SessionState.Hosting] session is live it renders the QR code, the 6-digit code and the
 * connected-device count. Embedded in the new-game screen so the roster syncs as players are added.
 */
@Composable
fun HostJoinSection(
    container: AppContainer,
    sessionState: SessionState,
    bluetoothStatusProvider: () -> BluetoothStatus,
    onOpenForJoins: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val btStatus = remember(tick, sessionState) { bluetoothStatusProvider() }
    var error by remember { mutableStateOf<String?>(null) }

    // Request BLUETOOTH_CONNECT (API 31+) on first show so the button can enable.
    val connectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { tick++ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !container.bluetoothRfcommManager.hasConnectPermission()
        ) {
            connectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun discoverableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
            BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
            BluetoothRfcommManager.DISCOVERABLE_DURATION_SECONDS,
        )

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            error = null
            onOpenForJoins()
        } else {
            error = context.getString(R.string.p2p_discoverable_denied)
        }
    }

    val advertiseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            discoverableLauncher.launch(discoverableIntent())
        } else {
            error = context.getString(R.string.p2p_bluetooth_permission)
        }
    }

    fun requestOpenForJoins() {
        error = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !container.bluetoothRfcommManager.hasAdvertisePermission()
        ) {
            advertiseLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            discoverableLauncher.launch(discoverableIntent())
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.p2p_join_session),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        val hosting = sessionState as? SessionState.Hosting
        if (hosting == null) {
            OutlinedButton(
                onClick = { requestOpenForJoins() },
                enabled = btStatus == BluetoothStatus.READY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.p2p_open_for_joins))
            }
            bluetoothHint(btStatus)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        } else {
            val qr: Bitmap = remember(hosting.payload) {
                QrCodeHelper.generate(SyncProtocol.encodeHandshake(hosting.payload), QR_SIZE_PX)
            }
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = stringResource(R.string.p2p_scan_qr),
                modifier = Modifier.size(220.dp),
            )
            Text(stringResource(R.string.p2p_session_code_label), style = MaterialTheme.typography.labelMedium)
            Text(hosting.sessionCode, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(
                    R.string.p2p_connected_count,
                    hosting.connectedDevices.size,
                    hosting.maxDevices,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            hosting.connectedDevices.forEach { device ->
                Text(
                    text = stringResource(R.string.p2p_device_joined_short, device.deviceName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun bluetoothHint(status: BluetoothStatus): String? = when (status) {
    BluetoothStatus.READY -> null
    BluetoothStatus.UNSUPPORTED -> stringResource(R.string.p2p_bluetooth_unsupported)
    BluetoothStatus.DISABLED -> stringResource(R.string.p2p_bluetooth_off_body)
    BluetoothStatus.PERMISSION_MISSING -> stringResource(R.string.p2p_bluetooth_permission)
}
