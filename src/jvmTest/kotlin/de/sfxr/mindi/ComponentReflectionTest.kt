package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.PostConstruct
import de.sfxr.mindi.annotations.PreDestroy
import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import de.sfxr.mindi.reflect.reflectConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

class ComponentReflectionTest {

    @ComponentAnnotation("test_component")
    @Qualifier("qualified_name")
    class TestComponent {
        @Autowired
        private lateinit var dependency: String

        @PostConstruct
        fun initialize() {}

        @PreDestroy
        fun cleanup() {}
    }

    @Test
    fun testReflectComponentWithAnnotations() {
        val component = Reflector.Default.reflect<TestComponent>()

        // Check component names from annotations
        assertEquals(2, component.names.size)
        assertEquals("test_component", component.names[0])
        assertEquals("qualified_name", component.names[1])

        // Check fields reflect the autowired dependency
        assertEquals(1, component.fields.size)
        assertEquals(String::class, (component.fields[0] as Dependency.Single).klass)

        // Check lifecycle methods were properly reflected
        assertNotNull(component.postConstruct)
        assertNotNull(component.close)
    }

    // Move the function outside the test method to fix the reflection issue
    fun createTestComponent(name: String): TestComponent = TestComponent()

    @Test
    fun testReflectFunction() {
        val component = with(Reflector.Default) {
            reflectConstructor(::createTestComponent, listOf("factory_component"), true)
        }

        // Check function component properties
        assertEquals("factory_component", component.name)
        assertEquals(TestComponent::class, component.klass)
        assertTrue(component.primary)

        // Check constructor args
        assertEquals(1, component.constructorArgs.size)
        assertEquals(String::class, (component.constructorArgs[0] as Dependency.Single).klass)
    }
}
