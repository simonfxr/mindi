package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OptionalDependencyTest {

    // Component with optional constructor dependencies
    @Component
    class WithOptionalConstructorDep(
        // Optional constructor parameter with default value
        @Autowired(required = false)
        val optionalDependency: MissingComponent? = null,

        // Optional constructor parameter with default value (non-nullable)
        val optionalWithDefault: String = "default-value"
    )

    // Component with optional field dependencies
    @Component
    class WithOptionalFieldDep {
        // Optional field that will be null if not found
        @Autowired(required = false)
        var optionalField: MissingComponent? = null

        // Setter method with optional dependency
        private var _setterCalled = false
        private var _setterValue: MissingComponent? = null

        @Autowired(required = false)
        fun setOptionalValue(value: MissingComponent? = null) {
            _setterCalled = true
            _setterValue = value
        }

        // Accessor methods for test verification
        fun wasSetterCalled() = _setterCalled
        fun getSetterValue() = _setterValue
    }

    // This class doesn't exist as a component, which tests the optional dependency feature
    class MissingComponent

    @Test
    fun testOptionalConstructorDependenciesWithReflection() {
        // Reflect the component using the Reflector
        val component = with(Reflector.Default) {
            reflect(WithOptionalConstructorDep::class)
        }

        // Create a plan with just this component
        val plan = Plan.build(listOf(component))
        val context = Context.instantiate(plan)

        // Get the instantiated component from context using the new API
        val instance = context.get<WithOptionalConstructorDep>()

        // Verify optional dependency is null (not found)
        assertNull(instance.optionalDependency)

        // Verify default value was used
        assertEquals("default-value", instance.optionalWithDefault)

        context.close()
    }

    @Test
    fun testOptionalFieldDependenciesWithReflection() {
        // Reflect the component using the Reflector
        val component = with(Reflector.Default) {
            reflect(WithOptionalFieldDep::class)
        }

        // Create a plan with just this component
        val plan = Plan.build(listOf(component))
        val context = Context.instantiate(plan)

        // Get the instantiated component from context using the new API
        val instance = context.get<WithOptionalFieldDep>()

        // Verify optional field is null (not found)
        assertNull(instance.optionalField)

        // Verify setter was called with null value
        assertEquals(true, instance.wasSetterCalled())
        assertNull(instance.getSetterValue())

        // Test that missing component returns null with getOrNull()
        val missingComponent = context.getOrNull<MissingComponent>()
        assertNull(missingComponent)

        // Test that existing component is found with getOrNull()
        val existingComponent = context.getOrNull<WithOptionalFieldDep>()
        assertNotNull(existingComponent)
        assertSame(instance, existingComponent)

        // Test that getAll() returns all instances of a type (empty if none exist)
        val allMissingComponents = context.getAll<MissingComponent>()
        assertTrue(allMissingComponents.isEmpty())

        // Test that getAll() for an existing component includes it in the result
        val allFieldDepComponents = context.getAll<WithOptionalFieldDep>()
        assertEquals(1, allFieldDepComponents.size)
        assertTrue(allFieldDepComponents.values.contains(instance))

        context.close()
    }
}