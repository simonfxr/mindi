package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SuperTypeGenericInjectionTest {
    // Baz is the generic type parameter that will be injected
    class Baz(val value: String = "test")

    // Bar is a generic class that takes a type parameter T
    open class Bar<T> {
        @Autowired
        var generic: T? = null
    }

    // Foo extends Bar with a concrete type parameter Baz
    @Component
    class Foo : Bar<Baz>()

    @Test
    fun testGenericSuperTypeReflection() {
        // Test that reflection correctly identifies the generic supertype
        val component = Reflector.Default.reflect<Foo>()

        // Verify that Bar<Baz> is recognized as a supertype
        assertTrue(typeOf<Bar<Baz>>() in component.superTypes,
            "Should find Bar<Baz> in supertypes")
    }

    @Test
    fun testGenericSuperTypeInjection() {
        // Create a context with our components
        val components = listOf(
            Reflector.Default.reflect<Foo>(),
            Reflector.Default.reflect<Baz>(),
        )

        // Instantiate the context
        val context = Context.instantiate(components)

        // Get the Foo instance and verify its generic field was properly injected
        val foo = context.get<Foo>()

        // The generic field should be injected with a Baz instance
        assertNotNull(foo.generic, "The generic field should be injected")
        assertTrue(foo.generic is Baz, "The generic field should be a Baz instance")
        assertEquals("test", (foo.generic as Baz).value)
    }

    @Test
    fun testGenericTypeInjection() {
        // Create a context with our components
        val components = listOf(
            Reflector.Default.reflect<Bar<Baz>>(),
            Reflector.Default.reflect<Baz>(),
        )

        // Instantiate the context
        val context = Context.instantiate(components)

        // Get the Bar instance and verify its generic field was properly injected
        val bar = context.get<Bar<Baz>>()

        // The generic field should be injected with a Baz instance
        assertNotNull(bar.generic, "The generic field should be injected")
        assertTrue(bar.generic is Baz, "The generic field should be a Baz instance")
        assertEquals("test", (bar.generic as Baz).value)
    }


}