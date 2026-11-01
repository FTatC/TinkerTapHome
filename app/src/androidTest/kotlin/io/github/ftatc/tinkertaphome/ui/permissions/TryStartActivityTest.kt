package io.github.ftatc.tinkertaphome.ui.permissions

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TryStartActivityTest {

    @Test
    fun returnsFalseInsteadOfCrashingWhenNoActivityHandlesTheIntent() {
        // Regressionstest: startActivity kastade ActivityNotFoundException och kraschade appen
        // när systemets inställningsskärm saknades, till exempel på en hanterad enhet.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent("io.github.ftatc.tinkertaphome.test.NO_SUCH_ACTION")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        assertFalse(context.tryStartActivity(intent))
    }
}
