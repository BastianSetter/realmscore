package de.morzo.realmscore.data.p2p

import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.regex.Pattern

/**
 * Wraps [CompanionDeviceManager] for the P2P join flow (Phase 28).
 *
 * Because an app can't read its own Bluetooth MAC on Android 6+, the client can't be handed the
 * host's address directly. Instead the system pairs/associates the device for us: we hand CDM a
 * name filter (the host Bluetooth name from the handshake payload) and the system shows its own
 * device-picker dialog — **no `ACCESS_FINE_LOCATION` and no in-app scanning required**. The user
 * confirms, and we read the resolved MAC out of the association result, then open the RFCOMM socket.
 *
 * The CDM callback yields an [IntentSender] that must be launched from the UI via
 * `ActivityResultContracts.StartIntentSenderForResult`; [extractMacAddress] parses the returned
 * result intent.
 */
class CompanionDeviceHelper(private val context: Context) {

    private companion object {
        const val TAG = "P2PJoin"
    }

    fun isSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    /**
     * Starts a CDM association. [hostBluetoothName] (from the handshake) narrows the system picker to
     * the host; pass null/blank to let the user pick from all nearby devices (camera-less fallback).
     * On success the system invokes [onIntentSender] with a dialog to launch; failures go to [onFailure].
     *
     * [activityContext] **must** be the Activity context: `CompanionDeviceManager.associate()`
     * internally casts the context to an Activity, so the application context crashes with a
     * ClassCastException.
     */
    fun associate(
        activityContext: Context,
        hostBluetoothName: String?,
        onIntentSender: (IntentSender) -> Unit,
        onFailure: (CharSequence?) -> Unit,
    ) {
        val manager = activityContext.getSystemService(CompanionDeviceManager::class.java) ?: run {
            onFailure("CompanionDeviceManager unavailable")
            return
        }

        val filterBuilder = BluetoothDeviceFilter.Builder()
        if (!hostBluetoothName.isNullOrBlank()) {
            filterBuilder.setNamePattern(Pattern.compile(Pattern.quote(hostBluetoothName)))
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(!hostBluetoothName.isNullOrBlank())
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            // API 33+
            override fun onAssociationPending(intentSender: IntentSender) {
                Log.d(TAG, "callback.onAssociationPending → launching chooser")
                onIntentSender(intentSender)
            }

            // API 26–32
            @Deprecated("Replaced by onAssociationPending on API 33+", ReplaceWith(""))
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) {
                Log.d(TAG, "callback.onDeviceFound → launching chooser")
                onIntentSender(intentSender)
            }

            override fun onFailure(error: CharSequence?) {
                Log.d(TAG, "callback.onFailure error=$error")
                onFailure(error)
            }
        }

        Log.d(
            TAG,
            "associate() name='${hostBluetoothName ?: ""}' singleDevice=${!hostBluetoothName.isNullOrBlank()} sdk=${Build.VERSION.SDK_INT}",
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.associate(request, activityContext.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                manager.associate(request, callback, null)
            }
        } catch (e: Exception) {
            onFailure(e.message ?: "association_failed")
        }
    }

    /** Pulls the associated device's MAC out of the result intent returned by the launched dialog. */
    fun extractMacAddress(result: Intent?): String? {
        result ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = result.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java,
            )
            info?.deviceMacAddress?.toString()?.uppercase()
        } else {
            @Suppress("DEPRECATION")
            val device = result.getParcelableExtra<BluetoothDevice>(
                CompanionDeviceManager.EXTRA_DEVICE,
            )
            device?.address
        }
    }
}
