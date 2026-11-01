package io.github.ftatc.tinkertaphome.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Androids BLE-skanner som ett [Flow]. Innehåller ingen logik utöver att översätta callbacks,
 * eftersom koden bara kan köras mot riktig Bluetooth-hårdvara. Logiken ligger i [AndroidDeviceScanner].
 */
internal class AndroidBleScanSource @Inject constructor(
    @ApplicationContext context: Context,
) : BleScanSource {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    // Behörigheten kontrolleras av AndroidDeviceScanner innan skanningen startas.
    @SuppressLint("MissingPermission")
    override fun scanEvents(): Flow<RawScanEvent> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(RawScanEvent.Unavailable)
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toRawEvent())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toRawEvent()) }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(RawScanEvent.Failed(errorCode))
                close()
            }
        }

        try {
            scanner.startScan(null, scanSettings, callback)
        } catch (e: IllegalStateException) {
            // Bluetooth stängdes av precis innan. SecurityException släpps igenom till AndroidDeviceScanner.
            trySend(RawScanEvent.Unavailable)
            close()
        }

        awaitClose {
            // stopScan kastar om Bluetooth redan är avstängt. Då har skanningen ändå stoppats.
            runCatching { scanner.stopScan(callback) }
        }
    }

    private companion object {
        val scanSettings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
}

/**
 * Namnet läses från reklampaketet och inte från `BluetoothDevice.getName()`, som kräver
 * BLUETOOTH_CONNECT och kan ge ett gammalt cachat namn.
 */
private fun ScanResult.toRawEvent(): RawScanEvent.Result =
    RawScanEvent.Result(address = device.address, name = scanRecord?.deviceName, rssi = rssi)
