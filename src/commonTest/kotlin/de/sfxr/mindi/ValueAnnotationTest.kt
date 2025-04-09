package de.sfxr.mindi

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.annotations.PostConstruct
import de.sfxr.mindi.annotations.Value
import kotlin.test.*

/**
 * Test case to demonstrate the issue with @Value annotations and type conversion.
 *
 * This test reproduces the issue encountered in the webapp example where @Value annotations
 * with non-String types cause "argument type mismatch" errors during component instantiation.
 */
class ValueAnnotationTest {

    /**
     * Custom property resolver that loads values from a map.
     * Similar to the PropertyResolver in the webapp example.
     */
    class TestPropertyResolver : ValueResolver {
        // Sample properties similar to application.properties
        private val properties = mapOf(
            "server.port" to "8080",
            "server.host" to "localhost",
            "server.enabled" to "true"
        )

        override fun resolve(key: String): Any? {
            println("Resolving key: $key")

            // Direct property lookup
            val value = properties[key]
            println("Direct lookup of '$key' returned: '$value'")
            return value
        }
    }

    /**
     * Server component that directly uses @Value annotations with different types.
     * This doesn't work in the webapp example because of type conversion issues.
     */
    @Component
    class ServerComponentWithValueAnnotation(
        // This doesn't work - causes "argument type mismatch"
        @Value("\${server.port:8080}")
        val port: Int,

        @Value("\${server.host:localhost}")
        val host: String,

        @Value("\${server.enabled:true}")
        val enabled: Boolean
    ) {
        var initialized = false

        @PostConstruct
        fun initialize() {
            println("ServerComponent initialized with port=$port, host=$host, enabled=$enabled")
            initialized = true
        }
    }

    /**
     * Config pattern that works around the type conversion issue by
     * using String properties and manual conversion.
     */
    @Component
    class ServerConfig {
        @Value("\${server.port:8080}")
        var port: Int = -1

        @Value("\${server.host:localhost}")
        lateinit var host: String

        @Value("\${server.enabled:true}")
        var enabled: Boolean = false

        var initialized = false

        @PostConstruct
        fun initialize() {
            println("ServerConfig initialized: port=$port, host=$host, enabled=$enabled")
            initialized = true
        }
    }

    /**
     * Server component that uses the config pattern to access typed properties.
     * This works in the webapp example.
     */
    @Component
    class ServerComponentWithConfig(private val config: ServerConfig) {
        val port: Int
            get() = config.port

        val host: String
            get() = config.host

        val enabled: Boolean
            get() = config.enabled

        var initialized = false

        @PostConstruct
        fun initialize() {
            println("ServerComponent initialized with config: port=${config.port}, " +
                    "host=${config.host}, enabled=${config.enabled}")
            initialized = true
        }
    }

    /**
     * Test that demonstrates the issue with @Value annotations and type conversion.
     *
     * Expected behavior:
     * - Direct @Value injection with non-String types should work properly
     * - mindi should automatically convert String values to the proper types
     * - Both components should initialize successfully
     *
     * Actual behavior:
     * - ServerComponentWithValueAnnotation fails with "argument type mismatch"
     * - ServerComponentWithConfig works because we manually convert types
     */
    @Test
    fun testValueAnnotationWithTypeConversion() {
        // Custom property resolver
        val propertyResolver = TestPropertyResolver()

        // Scan for components
        try {
            // Create components with @Value annotations
            ServerComponentWithValueAnnotation(8080, "localhost", true)
            val serverConfig = ServerConfig()
            val componentWithConfig = ServerComponentWithConfig(serverConfig)

            // First test - direct @Value injection - this currently fails
            val directAnnotationTest = {
                // This would normally come from reflection/component scanning but
                // we create it directly for the test
                val component = Component { port: Int, host: String, enabled: Boolean ->
                    ServerComponentWithValueAnnotation(port, host, enabled)
                }
                .named("serverComponent")
                .requireValue(0, "\${server.port}")
                .requireValue(1, "\${server.host}")
                .requireValue(2, "\${server.enabled}")

                val plan = Plan.build(listOf(component))
                val context = Context.instantiate(plan, resolver = propertyResolver)

                val server = context.get<ServerComponentWithValueAnnotation>()
                assertTrue(server.initialized)
                assertEquals(8080, server.port)
                assertEquals("localhost", server.host)
                assertEquals(true, server.enabled)

                context.close()
            }

            // This should work but currently fails due to type conversion issues
            assertFails {
                directAnnotationTest()
            }

            // Second test - config pattern - this works
            val configPatternTest = {
                // Create the components for the config approach
                // Create a component with constructor args
                val configComponent = Component { port: Int, host: String, enabled: Boolean ->
                    serverConfig.apply {
                        this.port = port
                        this.host = host
                        this.enabled = enabled
                        initialize()
                    }
                }
                .named("serverConfig")
                .requireValue(0, "\${server.port:8080}")
                .requireValue(1, "\${server.host:localhost}")
                .requireValue(2, "\${server.enabled:true}")

                val serverComponent = Component { config: ServerConfig ->
                    componentWithConfig
                }
                .named("serverComponent")
                .onInit { initialize() }

                val plan = Plan.build(listOf(configComponent, serverComponent))
                val context = Context.instantiate(plan, resolver = propertyResolver)

                val config = context.get<ServerConfig>()
                assertTrue(config.initialized)
                assertEquals(8080, config.port)
                assertEquals("localhost", config.host)
                assertEquals(true, config.enabled)

                val server = context.get<ServerComponentWithConfig>()
                assertTrue(server.initialized)
                assertEquals(8080, server.port)
                assertEquals("localhost", server.host)
                assertEquals(true, server.enabled)

                context.close()
            }

            // This should work and does
            configPatternTest()

        } catch (e: Exception) {
            println("Test failed with exception: ${e.message}")
            e.printStackTrace()
            fail("Test failed with exception: ${e.message}")
        }
    }

    /**
     * This test demonstrates the expected behavior for @Value annotations.
     *
     * Expected behavior:
     * 1. ValueResolver.resolve() should return values as Strings
     * 2. ValueResolver.parseValue() should convert these Strings to the target type
     * 3. The DI container should use these to construct components with proper types
     */
    @Test
    fun testExpectedValueAnnotationBehavior() {
        val resolver = TestPropertyResolver()

        // Test basic value resolution
        val portValue = resolver.resolve("server.port")
        assertEquals("8080", portValue)

        // Test parsing to various types
        val portAsInt = resolver.parseValue(TypeProxy<Int>(), "8080")
        assertEquals(8080, portAsInt)

        val enabledAsBool = resolver.parseValue(TypeProxy<Boolean>(), "true")
        assertEquals(true, enabledAsBool)
    }
}
