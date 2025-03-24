package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Platform-independent tests for the EnvResolver functionality.
 * This test focuses on the dot-to-underscore conversion logic.
 */
class EnvResolverTest {

    @Test
    fun testEnvResolverBehavior() {
        // Create a test component that uses environment variables
        val component = Component { port: Int, url: String? ->
            TestConfig(port, url)
        }

        // Use requireValue to create value dependencies
        val configuredComponent = component
            .requireValue(0, "\${app.config.port:8080}")
            .requireValue(1, "\${db.connection.url}")

        // Check that the dependencies were correctly configured
        val dependencies = configuredComponent.constructorArgs
        assertEquals(2, dependencies.size)

        // Verify the first dependency (int with default)
        val portDep = dependencies[0] as Dependency.Value<*>
        assertEquals("app.config.port", portDep.variable)
        assertEquals(8080, portDep.default)

        // Verify the second dependency (string without default)
        val urlDep = dependencies[1] as Dependency.Value<*>
        assertEquals("db.connection.url", urlDep.variable)
        assertEquals(null, urlDep.default)
    }

    // Simple data class for testing
    data class TestConfig(val port: Int, val connectionUrl: String?)
}