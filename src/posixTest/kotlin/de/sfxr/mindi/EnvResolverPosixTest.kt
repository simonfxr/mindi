package de.sfxr.mindi

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.setenv
import platform.posix.unsetenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Ignore

/**
 * Tests for the POSIX implementation of environment variable resolution.
 *
 * Note: These tests require access to the POSIX environment variable API.
 * They're marked with @Ignore by default since the test runner environment
 * may not have the necessary system libraries (e.g., libcrypt.so.1).
 *
 * To run these tests, remove the @Ignore annotation when you have the
 * proper environment set up.
 */
@OptIn(ExperimentalForeignApi::class)
@Ignore
class EnvResolverPosixTest {

    @Test
    fun testPosixGetenv() {
        // NOTE: This test is ignored by default because it requires libcrypt.so.1
        // Remove the @Ignore annotation on the class to run it
        // Test setting and getting an environment variable
        val testKey = "MINDI_TEST_ENV_VAR"
        val testValue = "test_value_123"

        try {
            // Set environment variable using POSIX setenv
            setenv(testKey, testValue, 1)

            // Test our getenv implementation
            val resolvedValue = getenv(testKey)
            assertEquals(testValue, resolvedValue, "Should retrieve the environment variable")

            // Test non-existent variable
            val nonExistent = getenv("MINDI_NON_EXISTENT_VAR")
            assertNull(nonExistent, "Should return null for non-existent variables")
        } finally {
            // Clean up environment variables
            unsetenv(testKey)
        }
    }

    @Test
    fun testEnvResolver() {
        val testKey = "MINDI_TEST_RESOLVER"
        val testValue = "resolver_value"

        try {
            // Set environment variable
            setenv(testKey, testValue, 1)

            // Test via EnvResolver
            val envValue = EnvResolver.resolve(testKey)
            assertEquals(testValue, envValue, "EnvResolver should retrieve the environment variable")
        } finally {
            unsetenv(testKey)
        }
    }

    @Test
    fun testDotToUnderscoreConversion() {
        try {
            // Test dot to underscore conversion
            setenv("MINDI_TEST_DOTTED_VAR", "dotted_value", 1)
            val dottedValue = EnvResolver.resolve("MINDI_TEST.DOTTED.VAR")
            assertEquals("dotted_value", dottedValue, "Should convert dots to underscores")
        } finally {
            unsetenv("MINDI_TEST_DOTTED_VAR")
        }
    }
}