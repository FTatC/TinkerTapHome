package io.github.ftatc.tinkertaphome.data.ble.gatt

import android.bluetooth.BluetoothStatusCodes

/**
 * Gör om svaret från Android-anrop som svarar med en `BluetoothStatusCodes`-kod, till exempel
 * `writeCharacteristic` och `writeDescriptor`, till [GattRequest].
 *
 * Saknad behörighet och ett Bluetooth som har stängts av eller inte är igång får egna värden, så att användaren får rätt
 * förklaring i stället för att Bluetooth är upptaget. Övriga koder räknas som att Android vägrade.
 * Ren funktion utan Android-anrop, så att den kan testas på JVM.
 */
internal fun Int.statusToRequest(): GattRequest = when (this) {
    BluetoothStatusCodes.SUCCESS -> GattRequest.STARTED
    BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> GattRequest.PERMISSION_DENIED
    BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
    BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND,
    -> GattRequest.NOT_CONNECTED
    else -> GattRequest.REJECTED
}
