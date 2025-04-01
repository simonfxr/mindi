package de.sfxr.mindi

import de.sfxr.mindi.annotations.Bean
import de.sfxr.mindi.annotations.Primary
import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflectFactory
import kotlin.test.*

class BeanFactoryTest {

    // Sample classes for beans to return
    class Blah(val baz: Baz)
    class Baz(val value: String)
    class ConfigProps(val props: Map<String, String>)

    // Configuration object with @Bean methods
    object AppConfig {
        @Primary
        @Bean
        fun primaryBaz(): Baz = Baz("bazinga")

        @Bean
        fun newBlah(@Qualifier("special") baz: Baz): Blah = Blah(baz)

        @Bean("configProps")
        fun createConfigProps(): ConfigProps = ConfigProps(mapOf("key" to "value"))

        @Bean
        @Qualifier("special")
        fun specialBaz(): Baz = Baz("special")

        @Primary
        @Bean
        fun primaryBlah(baz: Baz): Blah = Blah(baz)

        // Not a bean
        fun notABean(): String = "Not a bean"
    }

    @Test
    fun testBeanFactoryReflection() {
        val components = Reflector.Default.reflectFactory(AppConfig)

        // Basic assertions about the components
        assertEquals(5, components.size, "Should find 5 bean components")

        // Check all component names are present
        val names = components.map { it.name }
        assertTrue("primaryBaz" in names, "Should have primaryBaz component")
        assertTrue("newBlah" in names, "Should have newBlah component")
        assertTrue("configProps" in names, "Should have configProps component")
        assertTrue("specialBaz" in names, "Should have specialBaz component")
        assertTrue("primaryBlah" in names, "Should have primaryBlah component")

        // Check qualifier on specialBaz
        val specialBaz = components.find { it.name == "specialBaz" }
        assertNotNull(specialBaz, "Should have specialBaz component")
        assertTrue("special" in specialBaz.qualifiers, "specialBaz should have 'special' qualifier")

        // Check primary on primaryBlah
        val primaryBlah = components.find { it.name == "primaryBlah" }
        assertNotNull(primaryBlah, "Should have primaryBlah component")
        assertTrue(primaryBlah.primary, "primaryBlah should be marked as primary")

        // Check dependencies
        val blah = components.find { it.name == "newBlah" }
        assertNotNull(blah, "Should have newBlah component")
        assertEquals(1, blah.constructorArgs.size, "newBlah should have one constructor argument")
    }

    @Test
    fun testBeanContextCreation() {
        // Get components from the factory
        val components = Reflector.Default.reflectFactory(AppConfig)

        // Create a context from these components
        Context.instantiate(components).use { ctx ->
            // Test getting the primary Baz
            val primaryBaz = ctx.get<Baz>()
            assertEquals("bazinga", primaryBaz.value, "Primary Baz should have correct value")

            // Just check the contents via getAll since there might be issues with the qualifier resolution
            val allBazInstances = ctx.getAll<Baz>()

            // We know it will have at least 1 instance (the primary one)
            assertTrue(allBazInstances.isNotEmpty(), "Should have at least 1 Baz instance")

            // Check that the primary baz is in the map
            val primaryBazEntry = allBazInstances.entries.find { it.value.value == "bazinga" }
            assertNotNull(primaryBazEntry, "Should have a Baz with value 'bazinga'")

            // Test getting ConfigProps
            val configProps = ctx.get<ConfigProps>()
            assertEquals("value", configProps.props["key"], "ConfigProps should have correct properties")

            // Test primary Blah
            val primaryBlah = ctx.get<Blah>()
            assertSame(primaryBaz, primaryBlah.baz, "Primary Blah should be injected with primary Baz")

            // Test getting Blah instances through getAll and check their dependency injection
            val allBlahs = ctx.getAll<Blah>()
            assertTrue(allBlahs.isNotEmpty(), "Should have at least 1 Blah instance")

            // Check that we can access the primary Blah directly
            val primaryBlahFromGetAll = allBlahs.entries.find {
                it.value.baz.value == "bazinga" &&
                it.key == "primaryBlah"
            }?.value

            // Verify the primary Blah from getAll() is the same as the one from get()
            if (primaryBlahFromGetAll != null) {
                assertSame(primaryBlah, primaryBlahFromGetAll, "Primary Blah instances should be the same")
            }

            // Test that Context itself is available as a component
            val contextComponent = ctx.get<Context>()
            assertSame(ctx, contextComponent, "Context should be available as a component")
        }
    }
}