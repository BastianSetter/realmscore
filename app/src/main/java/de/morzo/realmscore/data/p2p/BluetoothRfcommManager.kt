package de.morzo.realmscore.data.p2p

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM transport for P2P sync (Phase 28).
 *
 * The host calls [openServerSocket] then [accept] (in a loop) to take in clients; the client calls
 * [connect] with the host MAC obtained from a CompanionDeviceManager association. **No discovery is
 * ever started** — [connect] is a direct lookup + connect — so the feature needs no
 * `ACCESS_FINE_LOCATION`.
 *
 * On API 31+ the connect/listen/accept calls require the runtime [Manifest.permission.BLUETOOTH_CONNECT]
 * permission; callers must check [hasConnectPermission] first.
 */
class BluetoothRfcommManager(private val context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isBluetoothSupported: Boolean get() = adapter != null

    @get:SuppressLint("MissingPermission") // gated by hasConnectPermission() at the call sites
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /** The local adapter name the host advertises in its handshake payload (not the MAC). */
    @SuppressLint("MissingPermission")
    fun localBluetoothName(): String? = adapter?.name

    /** BLUETOOTH_CONNECT is a runtime permission only from API 31; below that it is install-time. */
    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * BLUETOOTH_ADVERTISE is required from API 31 to make the device discoverable (the host needs this
     * so the CompanionDeviceManager picker can find it). Below API 31 it is covered by BLUETOOTH_ADMIN.
     */
    fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    /** Host: open the RFCOMM server socket advertising our [SERVICE_UUID]. */
    @SuppressLint("MissingPermission")
    suspend fun openServerSocket(): BluetoothServerSocket = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bluetooth not available")
        a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
    }

    /** Host: block until a client connects (call in a loop). Returns the per-client connection. */
    @SuppressLint("MissingPermission")
    suspend fun accept(serverSocket: BluetoothServerSocket): P2PConnection =
        withContext(Dispatchers.IO) {
            val socket = serverSocket.accept()
            BluetoothP2PConnection(socket)
        }

    /** Client: connect directly to a host by MAC (no scan). */
    @SuppressLint("MissingPermission")
    suspend fun connect(macAddress: String): P2PConnection = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bluetooth not available")
        val device = a.getRemoteDevice(macAddress)
        val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
        socket.connect()
        BluetoothP2PConnection(socket)
    }

    companion object {
        private const val SERVICE_NAME = "RealmScore"

        /** How long the host stays discoverable for the join window (seconds; Android caps at 300). */
        const val DISCOVERABLE_DURATION_SECONDS = 300

        /**
         * Fixed RFCOMM service UUID — both sides must use the identical value. A real, generated
         * v4 UUID (the spec's `…-realmscore01` placeholder is not valid hex).
         */
        val SERVICE_UUID: UUID = UUID.fromString("7f3b2c10-9a4d-4e6f-b1a2-2c0f5e8a9d01")
    }
}

/** [P2PConnection] backed by a connected [BluetoothSocket] with newline-delimited JSON framing. */
private class BluetoothP2PConnection(
    private val socket: BluetoothSocket,
) : P2PConnection {

    private val output: OutputStream = socket.outputStream
    private val reader: BufferedReader = socket.inputStream.bufferedReader()
    private val writeLock = Mutex()

    @SuppressLint("MissingPermission")
    override val remoteDeviceName: String? = try {
        socket.remoteDevice?.name
    } catch (_: SecurityException) {
        null
    }

    override suspend fun send(message: SyncMessage) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            output.write((SyncProtocol.encode(message) + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    override fun receive(): Flow<SyncMessage> = flow {
        while (true) {
            val line = try {
                reader.readLine()
            } catch (_: IOException) {
                null
            } ?: break // null => peer closed the stream (or socket closed)
            if (line.isBlank()) continue
            val message = try {
                SyncProtocol.decode(line)
            } catch (_: Exception) {
                null // skip a malformed line rather than tearing the session down
            }
            if (message != null) emit(message)
        }
    }.flowOn(Dispatchers.IO)

    override fun isConnected(): Boolean = socket.isConnected

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
            // already closed
        }
    }
}
