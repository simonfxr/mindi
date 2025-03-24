package de.sfxr.mindi

import kotlin.reflect.typeOf
import kotlin.test.*

class DependencyTest {

    @Test
    fun testMapDependency() {
        val dep = Dependency(typeOf<Map<String, AutoCloseable>>())
        assertIs<Dependency.Multiple>(dep)
        assertEquals(AutoCloseable::class, dep.klass)
    }

    // Interface for testing Multiple dependencies
    interface TestHandler

    // Component class that requires a map of handlers
    class TestManager {
        lateinit var handlers: Map<String, TestHandler>
    }

    @Test
    fun testRequiredMultipleDependencyNotFound() {
        // Create a component that requires a Map<String, TestHandler>
        val managerComponent = Component { -> TestManager() }
            .setting { it: Map<String, TestHandler> -> this.handlers = it }
            .named("testManager")
            .with(required = true)

        // Create a plan with just the manager component, but no handlers
        // This should cause "Failed to find provider for" exception during instantiation
        val exception = assertFailsWith<IllegalStateException> {
            val plan = Plan.build(listOf(managerComponent))
            Context.instantiate(plan)
        }

        // Check that the error message mentions the failure to find providers
        val errorMessage = exception.message ?: ""
        assertTrue(errorMessage.contains("Failed to find provider for"),
            "Error message should contain 'Failed to find provider for' but was: $errorMessage")

        // Check that the error message mentions the TestHandler type
        assertTrue(errorMessage.contains("TestHandler"),
            "Error message should mention TestHandler type but was: $errorMessage")
    }

    // Concrete implementations of TestHandler for successful dependency resolution
    class HandlerOne : TestHandler
    class HandlerTwo : TestHandler
    class HandlerThree : TestHandler

    @Test
    fun testMultipleDependencySuccessfullyResolved() {
        // Create a component that requires a Map<String, TestHandler>
        val managerComponent = Component { -> TestManager() }
            .setting { it: Map<String, TestHandler> -> this.handlers = it }
            .named("testManager")
            .with(required = true)

        // Create three handler components implementing TestHandler
        val handler1 = Component { -> HandlerOne() }
            .withSuperType<_, TestHandler>()  // Mark as implementing TestHandler
            .named("handler1")

        val handler2 = Component { -> HandlerTwo() }
            .withSuperType<_, TestHandler>()
            .named("handler2")

        val handler3 = Component { -> HandlerThree() }
            .withSuperType<_, TestHandler>()
            .named("handler3")

        // Create a plan with all components
        val plan = Plan.build(listOf(managerComponent, handler1, handler2, handler3))
        val context = Context.instantiate(plan)

        try {
            // Get the TestManager instance from the context
            val manager = context.instances.filterIsInstance<TestManager>().first()

            // Verify the Map<String, TestHandler> was correctly injected
            assertEquals(3, manager.handlers.size, "Should have 3 handlers in the map")

            // Verify map keys match component names
            assertTrue(manager.handlers.containsKey("handler1"), "Map should contain handler1")
            assertTrue(manager.handlers.containsKey("handler2"), "Map should contain handler2")
            assertTrue(manager.handlers.containsKey("handler3"), "Map should contain handler3")

            // Verify instances are of the correct type
            assertTrue(manager.handlers["handler1"] is HandlerOne, "handler1 should be a HandlerOne instance")
            assertTrue(manager.handlers["handler2"] is HandlerTwo, "handler2 should be a HandlerTwo instance")
            assertTrue(manager.handlers["handler3"] is HandlerThree, "handler3 should be a HandlerThree instance")
        } finally {
            context.close()
        }
    }
}