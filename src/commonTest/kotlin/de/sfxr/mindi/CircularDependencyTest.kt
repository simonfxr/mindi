package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CircularDependencyTest {
    // Direct constructor-based circular dependency classes
    class ServiceA(val serviceB: ServiceB)
    class ServiceB(val serviceC: ServiceC)
    class ServiceC(val serviceA: ServiceA)

    @Test
    fun testCircularDependencyDetection() {
        // Create components with constructor dependencies to form a cycle
        val componentA = Component { b: ServiceB -> ServiceA(b) }
            .named("serviceA")
            .with(required = true)  // This needs to be required to trigger resolution

        val componentB = Component { c: ServiceC -> ServiceB(c) }
            .named("serviceB")

        val componentC = Component { a: ServiceA -> ServiceC(a) }
            .named("serviceC")

        // This should detect the circular dependency during instantiation
        val exception = assertFailsWith<IllegalStateException> {
            val plan = Plan.build(listOf(componentA, componentB, componentC))
            Context.instantiate(plan)
        }

        // Check that the error message mentions "Circular dependency detected"
        assertTrue(exception.message?.contains("Circular dependency detected") == true,
            "Error message should contain 'Circular dependency detected' but was: ${exception.message}")

        // Check that all components are mentioned in the error message
        assertTrue(exception.message?.contains("serviceA") == true,
            "Error message should mention serviceA but was: ${exception.message}")
        assertTrue(exception.message?.contains("serviceB") == true,
            "Error message should mention serviceB but was: ${exception.message}")
        assertTrue(exception.message?.contains("serviceC") == true,
            "Error message should mention serviceC but was: ${exception.message}")
    }
}