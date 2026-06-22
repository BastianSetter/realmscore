package de.morzo.realmscore.ui.p2p

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionState

/**
 * Client join flow (Phase 28): scan the host QR (or type the 6-digit code), let the
 * CompanionDeviceManager find & associate the host, connect over Bluetooth, then reconcile each
 * remote participant to a local profile. No `ACCESS_FINE_LOCATION`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinSessionScreen(
    container: AppContainer,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: JoinSessionViewModel = viewModel(
        factory = JoinSessionViewModel.Factory(
            p2p = container.p2pSessionRepository,
            handshake = container.handshakeManager,
            profileRepo = container.profileRepository,
        ),
    )
    val sessionState by vm.sessionState.collectAsStateWithLifecycle()
    val participants by vm.incomingParticipants.collectAsStateWithLifecycle()
    val localProfiles by vm.localProfiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val rejoin by vm.rejoinInfo.collectAsStateWithLifecycle()

    var payload by remember { mutableStateOf<HandshakePayload?>(null) }

    // CompanionDeviceManager.associate() requires an Activity context (it casts internally), so
    // resolve the hosting Activity rather than passing the application context.
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // BLUETOOTH_CONNECT (API 31+) is needed to connect to the host. Track status so the screen
    // refreshes from the "permission missing" state to the scanner once granted.
    var btStatus by remember { mutableStateOf(vm.bluetoothStatus()) }
    val connectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        btStatus = vm.bluetoothStatus()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !container.bluetoothRfcommManager.hasConnectPermission()
        ) {
            connectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    // Silent rejoin (§6 #2): when this screen is opened with a persisted last host (the "erneut
    // beitreten" path), reconnect straight away — no QR. On failure the normal scanner shows as the
    // fallback (the host can re-display its QR). Fire once, only when nothing is already connecting.
    var rejoinAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(rejoin, btStatus, sessionState) {
        if (!rejoinAttempted && rejoin != null &&
            btStatus == BluetoothStatus.READY &&
            sessionState !is SessionState.Connected &&
            sessionState !is SessionState.Connecting
        ) {
            rejoinAttempted = true
            vm.rejoin()
        }
    }

    val cdmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val mac = container.companionDeviceHelper.extractMacAddress(result.data)
        val p = payload
        if (mac != null && p != null) {
            vm.connect(p, mac)
        } else {
            vm.setError("association_failed")
        }
    }

    fun startAssociation(p: HandshakePayload) {
        payload = p
        val act = activity
        if (act == null) {
            vm.setError("no_activity")
            return
        }
        container.companionDeviceHelper.associate(
            activityContext = act,
            hostBluetoothName = p.hostBluetoothName,
            onIntentSender = { sender ->
                cdmLauncher.launch(IntentSenderRequest.Builder(sender).build())
            },
            onFailure = { vm.setError(it?.toString()) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.p2p_join_session)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    sessionState is SessionState.Connected ->
                        JoinedContent(
                            participants = participants,
                            localProfiles = localProfiles,
                            assignments = assignments,
                            error = error,
                            onAssign = { incoming, localId -> vm.assignMerge(incoming, localId) },
                            onDone = onDone,
                        )

                    sessionState is SessionState.Connecting ->
                        CenterStatus(stringResource(R.string.p2p_connecting), showSpinner = true)

                    btStatus != BluetoothStatus.READY ->
                        CenterStatus(bluetoothMessage(btStatus), showSpinner = false)

                    else ->
                        ScanContent(
                            container = container,
                            error = error,
                            onQrScanned = { text ->
                                val parsed = vm.parseQr(text)
                                if (parsed != null) startAssociation(parsed)
                            },
                            onManualCode = { code ->
                                startAssociation(vm.payloadForManualCode(code))
                            },
                        )
                }
            }

            // Escape hatch: an auto-rejoin to the persisted host (§6 #2) otherwise hijacks this screen
            // with no way to scan a *different* session. While connecting/connected, let the user drop it
            // and forget the stored host so the QR scanner comes back.
            if (sessionState is SessionState.Connecting || sessionState is SessionState.Connected) {
                TextButton(
                    onClick = { vm.forgetLastSession() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.p2p_join_other_session))
                }
            }
        }
    }
}

@Composable
private fun ScanContent(
    container: AppContainer,
    error: String?,
    onQrScanned: (String) -> Unit,
    onManualCode: (String) -> Unit,
) {
    var manualMode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (!manualMode) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                QrScannerView(
                    onQrDetected = onQrScanned,
                    onPermissionDenied = { manualMode = true },
                )
            }
            TextButton(
                onClick = { manualMode = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.p2p_enter_code_instead))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.p2p_session_code_label))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.p2p_session_code_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onManualCode(code) },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.p2p_join_session))
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * Post-join view ("join adds a player"): we've announced ourselves to the host and now wait for the
 * host to start. Shows the live roster the host broadcasts so the joiner sees themselves + everyone
 * else appear in real time. Per roster profile a "Zuweisen" button lets the user merge that (foreign)
 * profile into an existing local profile (Profil-Rework Phase 6) — geräteunabhängig.
 */
@Composable
private fun JoinedContent(
    participants: List<ParticipantInfo>,
    localProfiles: List<JoinSessionViewModel.LocalProfile>,
    assignments: Map<String, String>,
    error: String?,
    onAssign: (ParticipantInfo, String) -> Unit,
    onDone: () -> Unit,
) {
    var assignFor by remember { mutableStateOf<ParticipantInfo?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.p2p_joined_waiting),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (error != null) {
            Text(
                text = when (error) {
                    JoinSessionViewModel.ERROR_ASSIGN_OWNER ->
                        stringResource(R.string.p2p_assign_owner_error)
                    else -> stringResource(R.string.p2p_assign_failed)
                },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            // Key by profileId: it is unique per participant, whereas several players added on the
            // same device (the host's owner + any extra locals) share one originDeviceId.
            items(participants, key = { it.profileId }) { participant ->
                // Once assigned, show "Zusammengeführt mit <Name>" instead of "Zuweisen"; the label
                // stays tappable so the merge can be redirected to a different local profile.
                val mergedWith = assignments[participant.profileId]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(participant.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { assignFor = participant }) {
                        Text(
                            if (mergedWith != null) {
                                stringResource(R.string.p2p_merged_with, mergedWith)
                            } else {
                                stringResource(R.string.p2p_assign_local_profile)
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(stringResource(R.string.p2p_reconciliation_done))
        }
    }

    assignFor?.let { incoming ->
        AssignLocalProfileDialog(
            incoming = incoming,
            localProfiles = localProfiles,
            onPick = { localId ->
                onAssign(incoming, localId)
                assignFor = null
            },
            onDismiss = { assignFor = null },
        )
    }
}

@Composable
private fun AssignLocalProfileDialog(
    incoming: ParticipantInfo,
    localProfiles: List<JoinSessionViewModel.LocalProfile>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p2p_assign_pick_title, incoming.name)) },
        text = {
            Column {
                if (localProfiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.p2p_assign_no_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    localProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(profile.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun CenterStatus(message: String, showSpinner: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(message, textAlign = TextAlign.Center)
    }
}

/** Walks the ContextWrapper chain to find the hosting Activity (Compose's LocalContext). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun bluetoothMessage(status: BluetoothStatus): String = when (status) {
    BluetoothStatus.READY -> ""
    BluetoothStatus.UNSUPPORTED -> stringResource(R.string.p2p_bluetooth_unsupported)
    BluetoothStatus.DISABLED -> stringResource(R.string.p2p_bluetooth_off_body)
    BluetoothStatus.PERMISSION_MISSING -> stringResource(R.string.p2p_bluetooth_permission)
}
