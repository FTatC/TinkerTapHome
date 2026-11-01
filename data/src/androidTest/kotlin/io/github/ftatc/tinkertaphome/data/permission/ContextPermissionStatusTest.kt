package io.github.ftatc.tinkertaphome.data.permission

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifierar att [ContextPermissionStatus] läser Androids riktiga behörighetsstatus.
 *
 * Testet beviljar bara behörigheter. Att återkalla en behörighet dödar processen, och i en
 * biblioteksmodul är det samma process som testet körs i. Logiken i [AndroidBlePermissionChecker]
 * testas därför på JVM med en fake.
 */
@RunWith(AndroidJUnit4::class)
class ContextPermissionStatusTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val status = ContextPermissionStatus(context)

    @Test
    fun reportsAGrantedPermissionAsGranted() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.BLUETOOTH_SCAN)

        assertTrue(status.isGranted(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun reportsAPermissionTheAppDoesNotDeclareAsNotGranted() {
        assertFalse(status.isGranted(Manifest.permission.CAMERA))
    }
}
