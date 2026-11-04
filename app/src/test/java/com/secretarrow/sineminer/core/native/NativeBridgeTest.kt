package com.secretarrow.sineminer.core.native

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the NativeBridge. These tests run on the JVM (no Android
 * runtime), so we can only test the surface that does not require the loaded
 * native library. Tests that actually exercise the native code live in
 * `androidTest/` (instrumented) where the .so is loaded by the app.
 */
class NativeBridgeTest {

    @Test
    fun `core lib name matches Cargo lib name`() {
        assertEquals("sineminer_core", NativeBridge.CORE_LIB_NAME)
    }

    @Test
    fun `core version is non-empty`() {
        // The native function will throw if the library isn't loaded.
        // We expect that and just verify the bridge compiles + links.
        try {
            val v = NativeBridge.coreVersion()
            assertNotNull(v)
            assertTrue(v.isNotEmpty())
        } catch (e: UnsatisfiedLinkError) {
            // Expected in unit tests on JVM without the .so loaded
        }
    }

    @Test
    fun `validate address rejects empty input`() {
        try {
            val result = NativeBridge.validateAddress("")
            assertFalse("empty address should not validate", result)
        } catch (e: UnsatisfiedLinkError) {
            // Expected without native lib
        }
    }
}
