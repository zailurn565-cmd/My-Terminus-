package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read ForgeShell app name and empty state string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ForgeShell", context.getString(R.string.app_name))
        assertEquals(
            "No hosts yet. Tap + to forge a connection.",
            context.getString(R.string.empty_hosts_message)
        )
    }
}
