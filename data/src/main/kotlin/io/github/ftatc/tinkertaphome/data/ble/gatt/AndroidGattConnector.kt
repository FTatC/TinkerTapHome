package io.github.ftatc.tinkertaphome.data.ble.gatt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.ftatc.tinkertaphome.domain.model.CharacteristicId
import javax.inject.Inject

/**
 * Androids GATT-anslutning bakom [GattConnector]. Innehåller bara översättningen mellan Androids
 * API:er och [GattEvent], eftersom koden bara kan köras mot riktig Bluetooth-hårdvara.
 *
 * Behörigheten kontrolleras innan anslutningen startas, men den kan tas bort när som helst. Varje
 * anrop fångar därför `SecurityException` och gör om den till data i stället för att kasta.
 */
internal class AndroidGattConnector @Inject constructor(
    @ApplicationContext private val context: Context,
) : GattConnector {

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    override fun connect(address: String, onEvent: (GattEvent) -> Unit): GattConnectStart {
        val enabledAdapter = adapter?.takeIf { it.isEnabled } ?: return GattConnectStart.BluetoothOff
        val bondReceiver = BondStateReceiver(address, onEvent)
        // EXPORTED krävs: meddelandet skickas av Bluetooth-processen, som räknas som en annan app.
        // Med NOT_EXPORTED kommer det aldrig fram (verifierat på enhet). Det är säkert, eftersom
        // ACTION_BOND_STATE_CHANGED är ett skyddat systemmeddelande som andra appar inte kan skicka.
        context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_EXPORTED)
        val started = startGatt(enabledAdapter, address, onEvent)
        return when (started) {
            is GattStart.Connected -> GattConnectStart.Started(AndroidGattHandle(context, started.gatt, bondReceiver))
            is GattStart.Refused -> started.reason.also { context.unregisterReceiver(bondReceiver) }
        }
    }

    private fun startGatt(adapter: BluetoothAdapter, address: String, onEvent: (GattEvent) -> Unit): GattStart =
        try {
            val device = adapter.getRemoteDevice(address)
            device.connectGatt(context, false, GattCallback(onEvent), BluetoothDevice.TRANSPORT_LE)
                ?.let(GattStart::Connected)
                ?: GattStart.Refused(GattConnectStart.BluetoothOff)
        } catch (_: SecurityException) {
            GattStart.Refused(GattConnectStart.PermissionDenied)
        }

    private sealed interface GattStart {
        class Connected(val gatt: BluetoothGatt) : GattStart
        class Refused(val reason: GattConnectStart) : GattStart
    }
}

/** Översätter Androids GATT-callbacks till [GattEvent]. */
private class GattCallback(private val onEvent: (GattEvent) -> Unit) : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        onEvent(GattEvent.ConnectionChanged(status, connected = newState == BluetoothProfile.STATE_CONNECTED))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        onEvent(GattEvent.ServicesDiscovered(status))
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        onEvent(GattEvent.CharacteristicRead(characteristic.id(), status, value))
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        onEvent(GattEvent.CharacteristicWritten(characteristic.id(), status))
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (descriptor.uuid == AndroidGattHandle.CCCD_UUID) {
            onEvent(GattEvent.NotificationsChanged(descriptor.characteristic.id(), status))
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        onEvent(GattEvent.CharacteristicChanged(characteristic.id(), value))
    }
}

/** Tar emot systemets meddelanden om parkoppling, men bara för enheten med [address]. */
private class BondStateReceiver(
    private val address: String,
    private val onEvent: (GattEvent) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        if (device?.address != address) return
        val state = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            else -> BondState.NONE
        }
        onEvent(GattEvent.BondChanged(state))
    }
}

internal fun BluetoothGattCharacteristic.id(): CharacteristicId = CharacteristicId(service.uuid, uuid)
