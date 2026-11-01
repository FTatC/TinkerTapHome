package io.github.ftatc.tinkertaphome.domain.ble

/** Fake för tester. Sätt [granted] för att simulera att användaren beviljar eller nekar. */
class FakeBlePermissionChecker(var granted: Boolean = false) : BlePermissionChecker {

    override val requiredPermissions: List<String> = listOf(SCAN, CONNECT)

    override fun isGranted(): Boolean = granted

    companion object {
        const val SCAN = "android.permission.BLUETOOTH_SCAN"
        const val CONNECT = "android.permission.BLUETOOTH_CONNECT"
    }
}
