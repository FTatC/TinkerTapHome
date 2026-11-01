package io.github.ftatc.tinkertaphome.data.permission

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Tunt lager runt Androids behörighetskontroll, så att logiken ovanför kan testas utan enhet. */
internal fun interface PermissionStatus {
    fun isGranted(permission: String): Boolean
}

internal class ContextPermissionStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionStatus {

    override fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
