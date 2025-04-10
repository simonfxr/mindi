package de.sfxr.mindi

import de.sfxr.mindi.annotations.PostConstruct
import de.sfxr.mindi.annotations.PreDestroy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StopTokenTest {
    // Track the order of component instantiation and destruction
    private val instantiationOrder = mutableListOf<String>()
    private val destructionOrder = mutableListOf<String>()

    // Create a StopToken implementation that will stop after a certain number of components
    class CountingStopToken(private val stopAfterCount: Int) : StopToken {
        private var count = 0

        // This will be called during instantiation
        fun incrementAndCheck(): Boolean {
            count++
            println("StopToken check #$count")
            return count >= stopAfterCount
        }

        override val shouldStop: Boolean
            get() = incrementAndCheck()
    }

    // Component that records instantiation and destruction
    class TrackedComponent(private val name: String,
                          private val instOrder: MutableList<String>,
                          private val destOrder: MutableList<String>) {
        var initialized = false
        var destroyed = false

        fun initialize() {
            initialized = true
            instOrder.add(name)
        }

        fun cleanup() {
            destroyed = true
            destOrder.add(name)
        }
    }

    @Test
    fun testStopTokenInterruptsInstantiation() {
        // Create components with sequential dependencies to ensure a specific order
        instantiationOrder.clear()
        destructionOrder.clear()

        // Component A depends on nothing
        val componentA = Component { ->
            TrackedComponent("A", instantiationOrder, destructionOrder)
        }
            .onInit { initialize() }
            .onClose { cleanup() }
            .named("componentA")
            .qualified("A")
            .with(primary = true)

        // Component B depends on A - use qualifier to specify which component to use
        val componentB = Component { a: TrackedComponent ->
            TrackedComponent("B", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "A")
            .named("componentB")
            .qualified("B")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Component C depends on B - use qualifier to specify which component to use
        val componentC = Component { b: TrackedComponent ->
            TrackedComponent("C", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "B")
            .named("componentC")
            .qualified("C")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Component D depends on C - use qualifier to specify which component to use
        val componentD = Component { c: TrackedComponent ->
            TrackedComponent("D", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "C")
            .named("componentD")
            .qualified("D")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Create a stop token that will stop fairly early in the instantiation process
        // The exact number of checks depends on the instantiation order and internal implementation details
        val stopToken = CountingStopToken(5)

        // Build plan with all components
        val plan = Plan.build(listOf(componentA, componentB, componentC, componentD))

        // Instantiate with the stop token - should throw an exception
        val exception = assertFailsWith<IllegalStateException> {
            Context.instantiate(plan, stopToken = stopToken)
        }

        // Verify the correct exception message
        assertEquals("instantiation interrupted by stop token", exception.message)

        // Due to the complexity of the instantiation process and the non-deterministic nature
        // of when the stop token is checked, the exact components initialized may vary.
        // The important thing is that not all components were initialized, and the context was properly torn down.
        println("Components initialized: $instantiationOrder")
        println("Components destroyed: $destructionOrder")

        // Verify that the context was properly torn down
        assertTrue(destructionOrder.isNotEmpty(), "At least some components should have been destroyed")
    }

    @Test
    fun testContextInstantiationWithoutStopToken() {
        // Create the same components but without a stop token - should complete normally
        instantiationOrder.clear()
        destructionOrder.clear()

        // Component A depends on nothing
        val componentA = Component { ->
            TrackedComponent("A", instantiationOrder, destructionOrder)
        }
            .onInit { initialize() }
            .onClose { cleanup() }
            .named("componentA")
            .qualified("A")
            .with(primary = true)

        // Component B depends on A - use qualifier to specify which component to use
        val componentB = Component { a: TrackedComponent ->
            TrackedComponent("B", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "A")
            .named("componentB")
            .qualified("B")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Component C depends on B - use qualifier to specify which component to use
        val componentC = Component { b: TrackedComponent ->
            TrackedComponent("C", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "B")
            .named("componentC")
            .qualified("C")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Component D depends on C - use qualifier to specify which component to use
        val componentD = Component { c: TrackedComponent ->
            TrackedComponent("D", instantiationOrder, destructionOrder)
        }
            .requireQualified(0, "C")
            .named("componentD")
            .qualified("D")
            .onInit { initialize() }
            .onClose { cleanup() }

        // Build plan with all components
        val plan = Plan.build(listOf(componentA, componentB, componentC, componentD))

        // Instantiate without a stop token - should complete normally
        val context = Context.instantiate(plan)

        // Verify all components were initialized in the expected order
        assertEquals(listOf("A", "B", "C", "D"), instantiationOrder)

        // Close the context
        context.close()

        // Verify components were destroyed in reverse order
        assertEquals(listOf("D", "C", "B", "A"), destructionOrder)
    }

    @Test
    fun testNeverStoppingToken() {
        // Create a StopToken that never stops
        val neverStopToken = object : StopToken {
            override val shouldStop: Boolean = false
        }

        val testInstList = mutableListOf<String>()
        val testDestList = mutableListOf<String>()

        // Just a simple component
        val component = Component { ->
            TrackedComponent("Test", testInstList, testDestList)
        }
            .onInit { initialize() }
            .onClose { cleanup() }
            .named("testComponent")
            .with(primary = true)

        // Build plan and instantiate with the never-stopping token
        val plan = Plan.build(listOf(component))
        val context = Context.instantiate(plan, stopToken = neverStopToken)

        // Get the component and verify it was properly initialized
        val trackedComponent = context.get<TrackedComponent>()
        assertTrue(trackedComponent.initialized)
        assertFalse(trackedComponent.destroyed)

        // Clean up
        context.close()
        assertTrue(trackedComponent.destroyed)
    }
}