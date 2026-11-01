package io.github.ftatc.tinkertaphome.domain.ble

/**
 * Kontrollerar om appen har de runtime-behörigheter som BLE kräver. Implementeras i `data`-lagret.
 *
 * Själva förfrågan till användaren görs i UI-lagrets behörighetsflöde (se CLAUDE.md), som använder
 * [requiredPermissions] så att samma lista både kontrolleras och efterfrågas.
 */
interface BlePermissionChecker {

    /** Behörigheterna som krävs, som Android-behörighetsnamn, till exempel `android.permission.BLUETOOTH_SCAN`. */
    val requiredPermissions: List<String>

    /** `true` om alla [requiredPermissions] är beviljade just nu. */
    fun isGranted(): Boolean
}
