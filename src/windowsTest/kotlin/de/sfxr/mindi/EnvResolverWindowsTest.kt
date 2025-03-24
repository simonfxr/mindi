package de.sfxr.mindi

import kotlinx.cinterop.ExperimentalForeignApi
import platform.windows.SetEnvironmentVariableW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Ignore

/**
 * Tests for the Windows implementation of environment variable resolution.
 *
 * Note: These tests require access to the Windows API.
 * They're marked with @Ignore by default since the test may run on
 * non-Windows platforms or in environments without the necessary libraries.
 *
 * To run these tests, remove the @Ignore annotation when testing on Windows.
 */
@OptIn(ExperimentalForeignApi::class)
@Ignore
class EnvResolverWindowsTest {

    @Test
    fun testWindowsGetenv() {
        // NOTE: This test is ignored by default because it requires Windows APIs
        // Remove the @Ignore annotation on the class to run it
        // Test setting and getting an environment variable
        val testKey = "MINDI_TEST_ENV_VAR"
        val testValue = "test_value_123"

        try {
            // Set environment variable using Win32 API
            SetEnvironmentVariableW(testKey, testValue)

            // Test our getenv implementation
            val resolvedValue = getenv(testKey)
            assertEquals(testValue, resolvedValue, "Should retrieve the environment variable")

            // Test non-existent variable
            val nonExistent = getenv("MINDI_NON_EXISTENT_VAR")
            assertNull(nonExistent, "Should return null for non-existent variables")
        } finally {
            // Clean up environment variables
            SetEnvironmentVariableW(testKey, null)
        }
    }

    @Test
    fun testEnvResolver() {
        val testKey = "MINDI_TEST_RESOLVER"
        val testValue = "resolver_value"

        try {
            // Set environment variable
            SetEnvironmentVariableW(testKey, testValue)

            // Test via EnvResolver
            val envValue = EnvResolver.resolve(testKey)
            assertEquals(testValue, envValue, "EnvResolver should retrieve the environment variable")
        } finally {
            SetEnvironmentVariableW(testKey, null)
        }
    }

    @Test
    fun testDotToUnderscoreConversion() {
        try {
            // Test dot to underscore conversion
            SetEnvironmentVariableW("MINDI_TEST_DOTTED_VAR", "dotted_value")
            val dottedValue = EnvResolver.resolve("MINDI_TEST.DOTTED.VAR")
            assertEquals("dotted_value", dottedValue, "Should convert dots to underscores")
        } finally {
            SetEnvironmentVariableW("MINDI_TEST_DOTTED_VAR", null)
        }
    }
}