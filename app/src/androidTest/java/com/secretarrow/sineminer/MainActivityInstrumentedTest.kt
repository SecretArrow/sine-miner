package com.secretarrow.sineminer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * The native library should be loaded by SineMinerApplication before tests run
 * (use a custom test runner if you need to control this).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @Test
    fun `application context package name matches`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.secretarrow.sineminer.debug", context.packageName.substringBefore(".debug"))
    }

    @Test
    fun `native library loads successfully`() {
        try {
            System.loadLibrary("sineminer_core")
            val version = com.secretarrow.sineminer.core.native.NativeBridge.coreVersion()
            assertNotNull(version)
            assertTrue(version.isNotEmpty())
        } catch (e: UnsatisfiedLinkError) {
            // The emulator must match the ABI we built for (x86_64 in CI).
            throw e
        }
    }
}
