package de.morzo.realmscore.domain.p2p.model

/**
 * Readiness of the local Bluetooth stack before a P2P session can start. The UI maps each value to a
 * hint: enable Bluetooth, grant the BLUETOOTH_CONNECT permission, or "device has no Bluetooth".
 */
enum class BluetoothStatus {
    READY,
    UNSUPPORTED,
    DISABLED,
    PERMISSION_MISSING,
}
