package io.github.ftatc.tinkertaphome.data.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Androids BLE-skanner som ett [Flow]. Innehåller bara översättningen mellan Androids callbacks och
 * [scanEventFlow], eftersom koden bara kan köras mot riktig Bluetooth-hårdvara.
 *
 * Behörigheten kontrolleras av [AndroidDeviceScanner] innan skanningen startas, men den kan tas bort
 * när som helst. Varje anrop till skannern fångar därför `SecurityException` och gör om den till en
 * händelse i stället för att kasta.
 */
internal class AndroidBleScanSource @Inject constructor(
    @ApplicationContext context: Context,
) : BleScanSource {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    override fun scanEvents(): Flow<RawScanEvent> = scanEventFlow { listener ->
        val scanner = adapter?.bluetoothLeScanner ?: return@scanEventFlow ScanStart.Unavailable
        startScan(scanner, listener.toScanCallback())
    }

    private fun startScan(scanner: BluetoothLeScanner, callback: ScanCallback): ScanStart =
        try {
            scanner.startScan(null, scanSettings, callback)
            ScanStart.Started { stopScan(scanner, callback) }
        } catch (e: SecurityException) {
            ScanStart.PermissionDenied
        } catch (e: IllegalStateException) {
            // Bluetooth stängdes av precis innan skanningen skulle starta.
            ScanStart.Unavailable
        }

    /**
     * Stoppar skanningen. Returnerar `false` om Android inte tog emot anropet, vilket är ofarligt:
     * saknas behörigheten eller är Bluetooth avstängt har Android redan stoppat skanningen själv.
     */
    private fun stopScan(scanner: BluetoothLeScanner, callback: ScanCallback): Boolean =
        try {
            scanner.stopScan(callback)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalStateException) {
            false
        }

    private companion object {
        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
}

private fun ScanListener.toScanCallback(): ScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        onResult(result.toRawEvent())
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach { onResult(it.toRawEvent()) }
    }

    override fun onScanFailed(errorCode: Int) {
        onFailed(errorCode)
    }
}

/**
 * Namnet läses från reklampaketet och inte från `BluetoothDevice.getName()`, som kräver
 * BLUETOOTH_CONNECT och kan ge ett gammalt cachat namn.
 */
private fun ScanResult.toRawEvent(): RawScanEvent.Result =
    RawScanEvent.Result(address = device.address, name = scanRecord?.deviceName, rssi = rssi)
