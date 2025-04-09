package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.EventListener
import de.sfxr.mindi.annotations.PostConstruct
import de.sfxr.mindi.annotations.PreDestroy
import de.sfxr.mindi.events.ContextClosedEvent
import de.sfxr.mindi.events.ContextRefreshedEvent
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ContextTest {
    class TestComponent {
        var initialized = false
        var destroyed = false

        @PostConstruct
        fun initialize() {
            initialized = true
        }

        @PreDestroy
        fun cleanup() {
            destroyed = true
        }
    }

    class Publisher {
        @Autowired
        lateinit var context: Context

        fun publish(event: String) {
            context.publishEvent(event)
        }
    }

    class Listener {
        var eventCount = 0

        @EventListener
        fun onEvent(event: String) {
            eventCount++
        }
    }

    @Test
    fun testComponentLifecycle() {
        val testComponent = TestComponent()

        val component = Component { -> testComponent }
            .onInit { initialize() }
            .onClose { cleanup() }
            .named("testComponent")

        val plan = Plan.build(listOf(component))
        val context = Context.instantiate(plan)

        // Check initialization
        assertTrue(testComponent.initialized)
        assertFalse(testComponent.destroyed)

        // Close context and check cleanup
        context.close()
        assertTrue(testComponent.destroyed)
    }

    @Test
    fun testEventPublishing() {
        val eventListener = Listener()

        // Create components using the new API style
        val listenerComponent = Component { -> eventListener }
            .listening<String> { onEvent(it) }
            .named("eventListener")

        val publisherComponent = Component(::Publisher)
            .setting { it: Context -> context = it }
            .named("eventPublisher")

        val plan = Plan.build(listOf(listenerComponent, publisherComponent))
        val context = Context.instantiate(plan)

        // Initially no events received
        assertEquals(0, eventListener.eventCount)

        // Publish event and check it was received
        context.publishEvent("TestEvent")
        assertEquals(1, eventListener.eventCount)

        context.close()
    }

    @Test
    fun testNonRequiredComponentsAreNotInstantiated() {
        var requiredComponentInstantiated = false
        var nonRequiredComponentInstantiated = false

        // Create a required component
        val requiredComponent = Component { ->
                requiredComponentInstantiated = true
                TestComponent()
            }
            .named("requiredComponent")

        // Create a non-required component
        val nonRequiredComponent = Component { ->
                nonRequiredComponentInstantiated = true
                TestComponent()
            }
            .named("nonRequiredComponent").with(required = false)

        // Build plan with both components
        val plan = Plan.build(listOf(requiredComponent, nonRequiredComponent))
        val context = Context.instantiate(plan)

        // The required component should be instantiated
        assertTrue(requiredComponentInstantiated)

        // The non-required component should NOT be instantiated
        // as it was not referenced by any other component
        assertFalse(nonRequiredComponentInstantiated)

        context.close()
    }

    @Test
    fun testOptionalDependenciesWithDefaultValues() {
        // Default values for optional components
        val defaultObject = "DefaultValue"
        var injectedValue: Any? = null

        // Component with optional constructor dependency with a non-required dependency
        val componentWithOptionalConstructorArg = Component { args: Int? ->
            val dependency = args ?: defaultObject
            injectedValue = dependency
            dependency.toString()
        }
        .named("componentWithOptionalArg")

        // Demonstrate the new requireQualified API by creating a component with qualified constructor args
        Component { args: String ->
            // Here args would be multiple components resolved by qualifier
            "Component with qualified args: $args"
        }
        // Set the qualifier "first" for the first constructor argument
        .requireQualified(0, "first")
        .named("qualifiedComponent")

        // Component with optional field dependency
        class ComponentWithOptionalField {
            var fieldValue: Any? = null
        }

        val testComponentWithField = ComponentWithOptionalField()
        val componentWithOptionalField = Component { -> testComponentWithField }
            .named("componentWithOptionalField")
            .setting(required = false) { it: Double? ->
                this.fieldValue = it ?: defaultObject
                injectedValue = this.fieldValue
            }

        // Create plan with both components
        val plan = Plan.build(listOf(componentWithOptionalConstructorArg, componentWithOptionalField))
        val context = Context.instantiate(plan)

        // For the component with optional constructor arg, the default value should be used
        assertEquals(defaultObject, injectedValue)

        context.close()
    }

    @Test
    fun testListenerExceptionHandlerIsCalled() {
        // Counter to track exception handler calls
        var exceptionHandlerCalled = false
        var exceptionCaught: Exception? = null
        var eventCaught: Any? = null
        var componentCaught: Component<*>? = null

        // Create an exception-throwing listener
        class ThrowingListener {
            @EventListener
            fun onEvent(event: String) {
                throw RuntimeException("Test exception")
            }
        }

        // Create a custom ListenerExceptionHandler
        val customExceptionHandler = object : Context.ListenerExceptionHandler {
            override fun handleListenerException(
                context: Context.ListenerExceptionHandler.ExceptionContext,
                exception: Exception
            ) {
                exceptionHandlerCalled = true
                exceptionCaught = exception
                eventCaught = context.event
                componentCaught = context.component
            }
        }

        // Create the throwing listener component
        val throwingListenerComponent = Component { -> ThrowingListener() }
            .listening<String> { onEvent(it) }
            .named("throwingListener")

        // Build plan with the component
        val plan = Plan.build(listOf(throwingListenerComponent))
        val context = Context.instantiate(plan)

        // Set the custom exception handler
        // The Context component has a field dependency on ListenerExceptionHandler
        val contextComponent = context.shared.components.first { it.klass == Context::class }
        val setExceptionHandler = contextComponent.setters.first()
        setExceptionHandler(context, customExceptionHandler)

        // Publish an event that will cause an exception
        val testEvent = "TestEvent"
        context.publishEvent(testEvent)

        // Verify the exception handler was called
        assertTrue(exceptionHandlerCalled)
        assertTrue(exceptionCaught is RuntimeException)
        assertEquals("Test exception", exceptionCaught?.message)
        assertEquals(testEvent, eventCaught)
        assertEquals(throwingListenerComponent, componentCaught)

        context.close()
    }

    @Test
    fun testContextLifecycleEvents() {
        // Create listeners that track both context events
        class ContextEventsListener {
            var refreshedEventReceived = false
            var closedEventReceived = false
            var refreshedContext: Context? = null
            var closedContext: Context? = null

            @EventListener
            fun onContextRefreshed(event: ContextRefreshedEvent) {
                refreshedEventReceived = true
                refreshedContext = event.context
            }

            @EventListener
            fun onContextClosed(event: ContextClosedEvent) {
                closedEventReceived = true
                closedContext = event.context
            }
        }

        val listener = ContextEventsListener()
        val component = Component { -> listener }
            .listening<ContextRefreshedEvent> { onContextRefreshed(it) }
            .listening<ContextClosedEvent> { onContextClosed(it) }
            .named("contextEventsListener")

        // Instantiate the context - should trigger ContextRefreshedEvent
        val context = Context.instantiate(listOf(component))

        // Verify ContextRefreshedEvent was received
        assertTrue(listener.refreshedEventReceived, "ContextRefreshedEvent should be received")
        assertNotNull(listener.refreshedContext, "Context should be available in the event")
        assertEquals(context, listener.refreshedContext, "Context in event should match our context")

        // Close the context - should trigger ContextClosedEvent
        context.close()

        // Verify ContextClosedEvent was received
        assertTrue(listener.closedEventReceived, "ContextClosedEvent should be received")
        assertNotNull(listener.closedContext, "Context should be available in the event")
        assertEquals(context, listener.closedContext, "Context in event should match our context")
    }

    @Test
    fun testContextCloseResilienceWithEventException() {
        // Create a listener that throws an exception when it receives ContextClosedEvent
        class ThrowingListener {
            var closedMethodCalled = false

            @EventListener
            fun onContextClosed(event: ContextClosedEvent) {
                closedMethodCalled = true
                throw RuntimeException("Test exception from listener")
            }
        }

        // Create a component that will be destroyed during context close
        class CleanupComponent {
            var destroyed = false

            @PreDestroy
            fun cleanup() {
                destroyed = true
            }
        }

        val throwingListener = ThrowingListener()
        val cleanupComponent = CleanupComponent()

        val listenerComponent = Component { -> throwingListener }
            .listening<ContextClosedEvent> { onContextClosed(it) }
            .named("throwingListener")

        val cleanupComponentObj = Component { -> cleanupComponent }
            .onClose { cleanup() }
            .named("cleanupComponent")

        // Create and immediately close the context
        val context = Context.instantiate(listOf(listenerComponent, cleanupComponentObj))

        // Close should not fail even though the event listener throws an exception
        try {
            context.close()
        } catch (e: Exception) {
            // The exception should be rethrown, but only after cleanup is done
            assertTrue(cleanupComponent.destroyed, "Component should be properly destroyed despite event exception")
            assertTrue(throwingListener.closedMethodCalled, "Event listener was called before exception")
            throw e
        }

        // If no exception caught, verify both the event handler ran and component was destroyed
        assertTrue(throwingListener.closedMethodCalled, "Event listener should be called")
        assertTrue(cleanupComponent.destroyed, "Component should be properly destroyed")
    }
}
