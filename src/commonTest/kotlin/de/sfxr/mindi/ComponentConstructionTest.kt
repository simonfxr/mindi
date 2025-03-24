package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for component construction using the pure Kotlin API.
 *
 * This is the multiplatform equivalent of the JVM-specific ComponentReflectionTest.
 */
class ComponentConstructionTest {

    class TestComponent {
        lateinit var dependency: String
        var initialized = false
        var cleaned = false

        fun initialize() {
            initialized = true
        }

        fun cleanup() {
            cleaned = true
        }
    }

    @Test
    fun testComponentWithMultipleNames() {
        // Create a component with multiple names using the builder API
        val component = Component { -> TestComponent() }
            .named("test_component")
            .named("qualified_name")

        // Check component names
        assertEquals(2, component.names.size)
        assertEquals("test_component", component.names[0])
        assertEquals("qualified_name", component.names[1])
    }

    @Test
    fun testComponentWithDependencies() {
        // Create a component with a field dependency
        val component = Component { -> TestComponent() }
            .setting { it: String -> dependency = it }
            .onInit { initialize() }
            .onClose { cleanup() }
            .named("test_component")

        // Check fields reflect the dependency
        assertEquals(1, component.fields.size)
        assertEquals(String::class, (component.fields[0] as Dependency.Single).klass)

        // Check lifecycle methods were properly set
        assertNotNull(component.postConstruct)
        assertNotNull(component.close)
    }

    // Function to test factory components
    fun createTestComponent(name: String): TestComponent = TestComponent()

    @Test
    fun testFunctionComponent() {
        // Create a component from a factory function
        val component = Component(::createTestComponent)
            .named("factory_component")
            .with(primary = true)

        // Check function component properties
        assertEquals("factory_component", component.name)
        assertEquals(TestComponent::class, component.klass)
        assertTrue(component.primary)

        // Check constructor args
        assertEquals(1, component.constructorArgs.size)
        assertEquals(String::class, (component.constructorArgs[0] as Dependency.Single).klass)
    }
}