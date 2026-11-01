package io.github.ftatc.tinkertaphome.data.permission

import android.Manifest
import io.github.ftatc.tinkertaphome.domain.repository.BlePermissionChecker
import javax.inject.Inject

internal class AndroidBlePermissionChecker @Inject constructor(
    private val status: PermissionStatus,
) : BlePermissionChecker {

    override val requiredPermissions: List<String> = REQUIRED_PERMISSIONS

    override fun isGranted(): Boolean = requiredPermissions.all(status::isGranted)

    companion object {
        /**
         * Från Android 12 krävs SCAN för att hitta enheter och CONNECT för att ansluta och läsa namn.
         * SCAN deklareras med `neverForLocation` i manifestet, så ingen platsbehörighet behövs.
         */
        val REQUIRED_PERMISSIONS: List<String> = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    }
}
