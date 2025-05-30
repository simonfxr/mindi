package de.sfxr.mindi

import kotlin.test.*

/**
 * Tests parent-child relationships in context hierarchies using the pure Kotlin API.
 *
 * This is the multiplatform equivalent of the JVM-specific ParentContextTest.
 */
class ParentContextApiTest {

    // Common base interface for service components
    interface Service {
        fun getName(): String
    }

    // Component in parent context
    class ParentService : Service {
        override fun getName(): String = "ParentService"
    }

    // Component in child context with same type
    class ChildService : Service {
        override fun getName(): String = "ChildService"
    }

    // Service consumer with dependency that should be resolved from parent by default
    class ServiceConsumer {
        // Using lateinit to match JVM version (@Autowired lateinit var)
        lateinit var service: Service
    }

    // Qualified components for testing resolution across contexts
    class QualifiedParentService {
        fun getData(): String = "Parent Data"
    }

    class QualifiedChildService {
        fun getData(): String = "Child Data"
    }

    // Component that uses qualified services
    class QualifiedConsumer {
        lateinit var parentService: QualifiedParentService
        lateinit var childService: QualifiedChildService
    }

    // Event test components
    class EventEmitter {
        lateinit var context: Context

        fun emitEvent(event: String) {
            context.publishEvent(event)
        }
    }

    class ParentListener {
        val events = mutableListOf<String>()

        fun handleEvent(event: String) {
            println("ParentListener: $event")
            events.add("Parent: $event")
        }
    }

    class ChildListener {
        val events = mutableListOf<String>()

        fun handleEvent(event: String) {
            println("ChildListener: $event")
            events.add("Child: $event")
        }
    }

    // Simple tracked component with lifecycle hooks
    class Tracked {
        var destroyed = false

        fun destroy() {
            destroyed = true
        }
    }

    @Test
    fun testBasicParentChildRelationship() {
        // Basic test components
        class ParentComponent
        class ChildComponent

        // Create parent context
        val parentComponent = Component { -> ParentComponent() }
            .named("parent-component")

        val parentPlan = Plan.build(listOf(parentComponent))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context
            val childComponent = Component { -> ChildComponent() }
                .named("child-component")

            val childPlan = Plan.build(listOf(childComponent), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Check parent relationship
                assertSame(parentContext, childContext.parent)
                assertEquals(1, childContext.parents.size)
                assertSame(parentContext, childContext.parents.first())

                // Verify instances are created in their respective contexts
                assertTrue(parentContext.instances.any { it is ParentComponent })
                assertTrue(childContext.instances.any { it is ChildComponent })

                // Child context should not contain parent instances
                assertFalse(childContext.instances.any { it is ParentComponent })

            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    @Test
    fun testDependencyResolutionFromParent() {
        // Create parent context with ParentService
        val parentService = Component { -> ParentService() }
            .withSuperType<_, Service>()
            .named("parentService")

        val parentPlan = Plan.build(listOf(parentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with ServiceConsumer, but no child service
            val serviceConsumer = Component { -> ServiceConsumer() }
                .setting(required = true) { it: Service -> this.service = it }
                .named("serviceConsumer")

            val childPlan = Plan.build(listOf(serviceConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Consumer should get the service from parent context
                val consumer = childContext.instances.filterIsInstance<ServiceConsumer>().first()
                assertNotNull(consumer.service)
                assertIs<ParentService>(consumer.service)
                assertEquals("ParentService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Simple test component classes with different implementations
     */
    class ParentComponent {
        fun getValue() = "parent"
    }

    class ChildComponent {
        fun getValue() = "child"
    }

    class ComponentConsumer {
        lateinit var parentComponent: ParentComponent
        lateinit var childComponent: ChildComponent
    }

    @Test
    fun testChildComponentOverridesParent() {
        // Create parent context with ParentComponent
        val parentComponent = Component { -> ParentComponent() }
            .named("parent-component")

        val parentPlan = Plan.build(listOf(parentComponent))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with child components
            val childComponent = Component { -> ChildComponent() }
                .named("child-component")

            val componentConsumer = Component { -> ComponentConsumer() }
                .setting(required = true) { it: ParentComponent -> this.parentComponent = it }
                .setting(required = true) { it: ChildComponent -> this.childComponent = it }
                .named("componentConsumer")

            // Create child context
            val childPlan = Plan.build(listOf(childComponent, componentConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get components from contexts
                val parentInstance = parentContext.instances.filterIsInstance<ParentComponent>().first()
                val childInstance = childContext.instances.filterIsInstance<ChildComponent>().first()

                // Check values
                assertEquals("parent", parentInstance.getValue())
                assertEquals("child", childInstance.getValue())

                // Verify the consumer can access both parent and child components
                val consumer = childContext.instances.filterIsInstance<ComponentConsumer>().first()
                assertNotNull(consumer.parentComponent)
                assertNotNull(consumer.childComponent)

                // Verify values from consumer's injected components
                assertEquals("parent", consumer.parentComponent.getValue())
                assertEquals("child", consumer.childComponent.getValue())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    @Test
    fun testQualifiedDependenciesInParentChild() {
        // Create parent context with qualified parent service
        val parentService = Component { -> QualifiedParentService() }
            .named("parent")

        val parentPlan = Plan.build(listOf(parentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with qualified child service and consumer
            val childService = Component { -> QualifiedChildService() }
                .named("child")

            val consumer = Component { -> QualifiedConsumer() }
                .setting(qualifier = "parent", required = true) { it: QualifiedParentService -> this.parentService = it }
                .setting(qualifier = "child", required = true) { it: QualifiedChildService -> this.childService = it }
                .named("qualifiedConsumer")

            val childPlan = Plan.build(listOf(childService, consumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Consumer should get services from both contexts based on qualifier
                val qualifiedConsumer = childContext.instances.filterIsInstance<QualifiedConsumer>().first()

                assertNotNull(qualifiedConsumer.parentService)
                assertNotNull(qualifiedConsumer.childService)

                assertEquals("Parent Data", qualifiedConsumer.parentService.getData())
                assertEquals("Child Data", qualifiedConsumer.childService.getData())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    @Test
    fun testEventPropagationAcrossContexts() {
        // Create parent context with listener
        val parentListener = Component { -> ParentListener() }
            .listening<String> { handleEvent(it) }
            .named("parentListener")

        val parentPlan = Plan.build(listOf(parentListener))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with emitter and its own listener
            val childListener = Component { -> ChildListener() }
                .listening<String> { handleEvent(it) }
                .named("childListener")

            val emitter = Component { -> EventEmitter() }
                .setting(required = true) { it: Context -> this.context = it }
                .named("eventEmitter")

            val childPlan = Plan.build(listOf(childListener, emitter), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get components
                val childListenerInstance = childContext.instances.filterIsInstance<ChildListener>().first()
                val parentListenerInstance = parentContext.instances.filterIsInstance<ParentListener>().first()
                val emitterInstance = childContext.instances.filterIsInstance<EventEmitter>().first()

                // Verify initial state
                assertTrue(childListenerInstance.events.isEmpty())
                assertTrue(parentListenerInstance.events.isEmpty())

                // Emit event from child context
                emitterInstance.emitEvent("Test Event")

                // Both listeners should receive the event
                assertEquals(1, childListenerInstance.events.size)
                assertEquals("Child: Test Event", childListenerInstance.events.first())

                assertEquals(1, parentListenerInstance.events.size)
                assertEquals("Parent: Test Event", parentListenerInstance.events.first())

                // Emit from parent context
                parentContext.publishEvent("Parent Event")

                // Only parent listener should receive this event
                assertEquals(1, childListenerInstance.events.size) // Still just 1
                assertEquals(2, parentListenerInstance.events.size) // Now 2
                assertEquals("Parent: Parent Event", parentListenerInstance.events[1])
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    @Test
    fun testMultiLevelContextHierarchy() {
        // Create a three-level context hierarchy (grandparent -> parent -> child)

        // Components for each level
        class GrandparentComponent {
            fun getLevel(): String = "Level 1"
        }

        class ParentComponent {
            fun getLevel(): String = "Level 2"
        }

        class ChildComponent {
            fun getLevel(): String = "Level 3"
        }

        // Component to test hierarchy traversal
        class HierarchyTraverser {
            lateinit var grandparent: GrandparentComponent
            lateinit var parent: ParentComponent
            lateinit var child: ChildComponent
        }

        // Create grandparent context
        val grandparentComponent = Component { -> GrandparentComponent() }
            .named("grandparentComponent")

        val grandparentPlan = Plan.build(listOf(grandparentComponent))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with grandparent as its parent
            val parentComponent = Component { -> ParentComponent() }
                .named("parentComponent")

            val parentPlan = Plan.build(listOf(parentComponent), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with parent as its parent
                val childComponent = Component { -> ChildComponent() }
                    .named("childComponent")

                val traverserComponent = Component { -> HierarchyTraverser() }
                    .setting(required = true) { it: GrandparentComponent -> this.grandparent = it }
                    .setting(required = true) { it: ParentComponent -> this.parent = it }
                    .setting(required = true) { it: ChildComponent -> this.child = it }
                    .named("hierarchyTraverser")

                val childPlan = Plan.build(listOf(childComponent, traverserComponent), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Check context hierarchy is correctly established
                    assertSame(parentContext, childContext.parent)
                    assertSame(grandparentContext, parentContext.parent)
                    assertNull(grandparentContext.parent)

                    // Check traverser can access components from all levels
                    val traverser = childContext.instances.filterIsInstance<HierarchyTraverser>().first()

                    assertNotNull(traverser.grandparent)
                    assertNotNull(traverser.parent)
                    assertNotNull(traverser.child)

                    assertEquals("Level 1", traverser.grandparent.getLevel())
                    assertEquals("Level 2", traverser.parent.getLevel())
                    assertEquals("Level 3", traverser.child.getLevel())

                } finally {
                    childContext.close()
                }
            } finally {
                parentContext.close()
            }
        } finally {
            grandparentContext.close()
        }
    }

    @Test
    fun testContextClosingPropagation() {
        // Create parent context with a tracked component
        val parentTrackedComponent = Component { -> Tracked() }
            .onClose { destroy() }
            .named("parentTracked")

        val parentPlan = Plan.build(listOf(parentTrackedComponent))
        val parentContext = Context.instantiate(parentPlan)

        // Get the tracked instance from parent context
        val parentTracked = parentContext.instances.filterIsInstance<Tracked>().first()

        // Create child context with a tracked component
        val childTrackedComponent = Component { -> Tracked() }
            .onClose { destroy() }
            .named("childTracked")

        val childPlan = Plan.build(listOf(childTrackedComponent), parentPlan)
        val childContext = Context.instantiate(childPlan, parentContext = parentContext)

        // Get the tracked instance from child context
        val childTracked = childContext.instances.filterIsInstance<Tracked>().first()

        // Verify initial state
        assertFalse(parentTracked.destroyed)
        assertFalse(childTracked.destroyed)

        // Close only the parent context
        parentContext.close()

        // Parent component should be destroyed
        assertTrue(parentTracked.destroyed)

        // Child component should remain intact when parent is closed
        // This verifies that child contexts are not automatically closed when parent contexts are closed
        assertFalse(childTracked.destroyed, "Child context components should remain intact when parent is closed")
    }
}