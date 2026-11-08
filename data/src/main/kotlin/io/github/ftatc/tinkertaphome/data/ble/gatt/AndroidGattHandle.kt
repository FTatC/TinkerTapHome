package io.github.ftatc.tinkertaphome.data.ble.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import java.util.UUID

/**
 * En öppen anslutning via Androids `BluetoothGatt`. Kan bara köras mot riktig Bluetooth-hårdvara,
 * så den innehåller ingen logik utöver översättningen till [GattRequest].
 */
internal class AndroidGattHandle(
    private val context: Context,
    private val gatt: BluetoothGatt,
    private val bondReceiver: BroadcastReceiver,
) : GattHandle {

    @Volatile
    private var closed = false

    // Varje Android-anrop har sin egen try/catch, så att lint kan se att SecurityException hanteras.

    override fun discoverServices(): GattRequest {
        if (closed) return GattRequest.REJECTED
        return try {
            gatt.discoverServices().toRequest()
        } catch (_: SecurityException) {
            GattRequest.PERMISSION_DENIED
        }
    }

    override fun isBonded(): Boolean = try {
        gatt.device.bondState == BluetoothDevice.BOND_BONDED
    } catch (_: SecurityException) {
        false
    }

    override fun createBond(): GattRequest {
        if (closed) return GattRequest.REJECTED
        return try {
            gatt.device.createBond().toRequest()
        } catch (_: SecurityException) {
            GattRequest.PERMISSION_DENIED
        }
    }

    override fun read(characteristic: CharacteristicId): GattRequest =
        withCharacteristic(characteristic) { found ->
            try {
                gatt.readCharacteristic(found).toRequest()
            } catch (_: SecurityException) {
                GattRequest.PERMISSION_DENIED
            }
        }

    override fun write(characteristic: CharacteristicId, value: ByteArray): GattRequest =
        withCharacteristic(characteristic) { found ->
            try {
                gatt.writeCharacteristic(found, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT).statusToRequest()
            } catch (_: SecurityException) {
                GattRequest.PERMISSION_DENIED
            }
        }

    override fun setNotifications(characteristic: CharacteristicId, enabled: Boolean): GattRequest =
        withCharacteristic(characteristic) { found ->
            val cccd = found.getDescriptor(CCCD_UUID) ?: return@withCharacteristic GattRequest.NOT_FOUND
            try {
                if (!gatt.setCharacteristicNotification(found, enabled)) return@withCharacteristic GattRequest.REJECTED
                gatt.writeDescriptor(cccd, cccdValue(found, enabled)).statusToRequest()
            } catch (_: SecurityException) {
                GattRequest.PERMISSION_DENIED
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        try {
            gatt.disconnect()
            gatt.close()
        } catch (_: SecurityException) {
            // Utan behörighet har Android redan stängt anslutningen.
        }
        context.unregisterReceiver(bondReceiver)
    }

    private fun withCharacteristic(
        id: CharacteristicId,
        block: (BluetoothGattCharacteristic) -> GattRequest,
    ): GattRequest {
        if (closed) return GattRequest.REJECTED
        val found = gatt.getService(id.service)?.getCharacteristic(id.characteristic) ?: return GattRequest.NOT_FOUND
        return block(found)
    }

    companion object {
        /** Client Characteristic Configuration Descriptor, som slår på notifieringar. Standard i Bluetooth-specifikationen. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

/** För Android-anrop som svarar `true` när operationen har startats. */
private fun Boolean.toRequest(): GattRequest = if (this) GattRequest.STARTED else GattRequest.REJECTED

/** För Android-anrop som svarar med en `BluetoothStatusCodes`-kod. */
private fun Int.statusToRequest(): GattRequest =
    if (this == BluetoothStatusCodes.SUCCESS) GattRequest.STARTED else GattRequest.REJECTED

/** Indikeringar används om characteristicen bara stöder dem, annars notifieringar. */
private fun cccdValue(characteristic: BluetoothGattCharacteristic, enabled: Boolean): ByteArray {
    val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    return when {
        !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        supportsNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    }
}
