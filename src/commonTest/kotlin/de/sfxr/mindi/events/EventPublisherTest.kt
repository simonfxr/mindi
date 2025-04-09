package de.sfxr.mindi.events

import de.sfxr.mindi.*
import de.sfxr.mindi.annotations.EventListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

class EventPublisherTest {
    // Event classes for testing
    class TestEvent(val data: String)
    class SpecializedTestEvent(val data: String, val specialData: Int)

    // Component that listens for events
    class TestEventListener {
        var eventsReceived = mutableListOf<TestEvent>()
        var specialEventsReceived = mutableListOf<SpecializedTestEvent>()

        @EventListener
        fun onTestEvent(event: TestEvent) {
            eventsReceived.add(event)
        }

        @EventListener
        fun onSpecializedEvent(event: SpecializedTestEvent) {
            specialEventsReceived.add(event)
        }
    }

    // Component that publishes events
    @ComponentAnnotation
    class TestEventPublisher(val publisher: EventPublisher<TestEvent>) {
        fun publishTestEvent(data: String) {
            publisher.publishEvent(TestEvent(data))
        }
    }

    // Component that publishes specialized events
    @ComponentAnnotation
    class SpecializedEventPublisher(val publisher: EventPublisher<SpecializedTestEvent>) {
        fun publishSpecializedEvent(data: String, specialData: Int) {
            publisher.publishEvent(SpecializedTestEvent(data, specialData))
        }
    }

    @Test
    fun testEventPublisherBasicFunctionality() {
        val listener = TestEventListener()

        // Create components
        val listenerComponent = Component { -> listener }
            .listening<TestEvent> { onTestEvent(it) }
            .named("testEventListener")

        val publisherComponent = Component(::TestEventPublisher)
            .named("testEventPublisher")

        // Create and initialize the context
        val context = Context.instantiate(listOf(listenerComponent, publisherComponent))

        // Get the publisher component
        val publisher = context.get<TestEventPublisher>()
        assertNotNull(publisher)

        // Initially no events received
        assertEquals(0, listener.eventsReceived.size)

        // Publish an event and verify it was received
        publisher.publishTestEvent("test-data")
        assertEquals(1, listener.eventsReceived.size)
        assertEquals("test-data", listener.eventsReceived[0].data)

        context.close()
    }

    @Test
    fun testEventPublisherWithSpecializedEvents() {
        val listener = TestEventListener()

        // Create components
        val listenerComponent = Component { -> listener }
            .listening<TestEvent> { onTestEvent(it) }
            .listening<SpecializedTestEvent> { onSpecializedEvent(it) }
            .named("testEventListener")

        val publisherComponent = Component(::SpecializedEventPublisher)
            .named("specializedEventPublisher")

        // Create and initialize the context
        val context = Context.instantiate(listOf(listenerComponent, publisherComponent))

        // Get the publisher component
        val publisher = context.get<SpecializedEventPublisher>()

        // Publish a specialized event using the specialized publisher
        publisher.publishSpecializedEvent("specialized-data", 42)

        // Verify the specialized event listener received the event
        assertEquals(1, listener.specialEventsReceived.size, "Specialized event listener should receive the event")

        // Verify event data
        val specializedEvent = listener.specialEventsReceived[0]
        assertEquals("specialized-data", specializedEvent.data)
        assertEquals(42, specializedEvent.specialData)

        context.close()
    }

    @Test
    fun testMultipleEventPublishers() {
        val listener = TestEventListener()

        // Create components
        val listenerComponent = Component { -> listener }
            .listening<TestEvent> { onTestEvent(it) }
            .listening<SpecializedTestEvent> { onSpecializedEvent(it) }
            .named("testEventListener")

        val publisherComponent = Component(::TestEventPublisher)
            .named("testEventPublisher")

        val specializedPublisherComponent = Component(::SpecializedEventPublisher)
            .named("specializedEventPublisher")

        // Create and initialize the context
        val context = Context.instantiate(listOf(
            listenerComponent,
            publisherComponent,
            specializedPublisherComponent
        ))

        // Get both publishers
        val publisher = context.get<TestEventPublisher>()
        val specializedPublisher = context.get<SpecializedEventPublisher>()

        // Publish events from both publishers
        publisher.publishTestEvent("regular-event")
        specializedPublisher.publishSpecializedEvent("special-event", 100)

        // Verify events were correctly dispatched
        assertEquals(1, listener.eventsReceived.size, "Should receive one regular event")
        assertEquals(1, listener.specialEventsReceived.size, "Should receive one specialized event")

        // Verify data in received events
        assertEquals("regular-event", listener.eventsReceived[0].data)
        assertEquals("special-event", listener.specialEventsReceived[0].data)
        assertEquals(100, listener.specialEventsReceived[0].specialData)

        context.close()
    }

    @Test
    fun testEventPublisherInternalImplementation() {
        val listener = TestEventListener()

        // Create components
        val listenerComponent = Component { -> listener }
            .listening<TestEvent> { onTestEvent(it) }
            .named("testEventListener")

        // Create and initialize the context
        val context = Context.instantiate(listOf(listenerComponent))

        // Create an EventPublisher manually to test its internal implementation
        val manualPublisher = EventPublisher<TestEvent>(context)

        // Publish an event using the internal implementation
        manualPublisher.publishEvent(TestEvent("manual-test"), TypeProxy<TestEvent>())

        // Verify the event was received
        assertEquals(1, listener.eventsReceived.size)
        assertEquals("manual-test", listener.eventsReceived[0].data)

        context.close()
    }

    @Test
    fun testEventsDuringContextConstruction() {
        // Create a TestEvent to track the order of component construction
        var constructionOrder = mutableListOf<String>()

        // Listener that should be constructed before publisher
        class InitTrackedListener {
            var eventsReceived = mutableListOf<TestEvent>()

            init {
                constructionOrder.add("listener")
            }

            @EventListener
            fun onTestEvent(event: TestEvent) {
                eventsReceived.add(event)
            }
        }

        // Publisher that depends on EventPublisher<TestEvent> and thus should be created
        // after all TestEvent listeners are ready
        class DelayedPublisher(val publisher: EventPublisher<TestEvent>) {
            init {
                constructionOrder.add("publisher")
                // Publish an event during construction
                publisher.publishEvent(TestEvent("construction-event"))
            }
        }

        // Create components
        val listener = InitTrackedListener()
        val listenerComponent = Component { -> listener }
            .listening<TestEvent> { onTestEvent(it) }
            .named("initTrackedListener")

        val publisherComponent = Component(::DelayedPublisher)
            .named("delayedPublisher")

        // Create context - this should construct the listener before the publisher
        val context = Context.instantiate(listOf(publisherComponent, listenerComponent))

        // Verify construction order - listener must be created before publisher
        assertEquals(2, constructionOrder.size, "Both components should be constructed")
        assertEquals("listener", constructionOrder[0], "Listener should be constructed first")
        assertEquals("publisher", constructionOrder[1], "Publisher should be constructed second")

        // Verify the event published during construction was received
        assertEquals(1, listener.eventsReceived.size, "Listener should receive the event published during construction")
        assertEquals("construction-event", listener.eventsReceived[0].data)

        context.close()
    }
}