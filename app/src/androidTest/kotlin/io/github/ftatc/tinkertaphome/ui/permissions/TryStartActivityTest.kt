package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TryStartActivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun returnsFalseInsteadOfCrashingWhenNoActivityHandlesTheIntent() {
        // Regressionstest: startActivity kastade ActivityNotFoundException och kraschade appen
        // när systemets inställningsskärm saknades, till exempel på en hanterad enhet.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent("io.github.ftatc.tinkertaphome.test.NO_SUCH_ACTION")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        assertFalse(context.tryStartActivity(intent))
    }

    @Test
    fun activityStarterCallsOnUnavailableWhenNoActivityHandlesTheIntent() {
        var unavailable = false
        composeRule.setContent {
            val start = activityStarter(
                intent = { Intent("io.github.ftatc.tinkertaphome.test.NO_SUCH_ACTION") },
                onUnavailable = { unavailable = true },
            )
            Button(onClick = start) { Text(START) }
        }

        composeRule.onNodeWithText(START).performClick()

        assertTrue(unavailable)
    }

    @Test
    fun tryLaunchReturnsFalseInsteadOfCrashingWhenNoActivityHandlesTheIntent() {
        // Regressionstest: launcher.launch kastade ActivityNotFoundException och kraschade appen
        // när systemets behörighetsdialog saknades.
        var launched: Boolean? = null
        composeRule.setContent {
            val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { }
            Button(onClick = { launched = launcher.tryLaunch(Intent("io.github.ftatc.tinkertaphome.test.NO_SUCH_ACTION")) }) {
                Text(START)
            }
        }

        composeRule.onNodeWithText(START).performClick()

        assertEquals(false, launched)
    }

    private companion object {
        const val START = "Starta"
    }
}
