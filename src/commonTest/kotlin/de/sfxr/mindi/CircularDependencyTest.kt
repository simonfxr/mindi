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

        // This should detect the circular dependency during instantiation and throw DependencyCycleException
        val exception = assertFailsWith<DependencyCycleException> {
            val plan = Plan.build(listOf(componentA, componentB, componentC))
            Context.instantiate(plan)
        }

        // Check that the error message mentions "Circular dependency detected"
        assertTrue(exception.message.contains("Circular dependency detected"),
            "Error message should contain 'Circular dependency detected' but was: ${exception.message}")

        // Check that all components are mentioned in the error message
        assertTrue(exception.message.contains("serviceA"),
            "Error message should mention serviceA but was: ${exception.message}")
        assertTrue(exception.message.contains("serviceB"),
            "Error message should mention serviceB but was: ${exception.message}")
        assertTrue(exception.message.contains("serviceC"),
            "Error message should mention serviceC but was: ${exception.message}")

        // Get the detailed representation and verify it contains expected parts
        val detailedMessage = exception.getDetailedCycleRepresentation()
        assertTrue(detailedMessage.contains("depends on"),
            "Detailed message should contain 'depends on' but was: $detailedMessage")
        assertTrue(detailedMessage.contains("To resolve this circular dependency"),
            "Detailed message should contain resolution hints but was: $detailedMessage")
    }

    // For now, we'll just focus on the core circular dependency test
    // A more complex test with mixed field/constructor dependencies can be added later
    // after examining the current component API more thoroughly
}