package de.sfxr.mindi

import de.sfxr.mindi.events.ContextClosedEvent
import de.sfxr.mindi.events.ContextRefreshedEvent
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreRegressionTest {
    class OptionalConsumer(val value: String?)
    class GenericValue<T>(val value: T)

    @Test
    fun missingQualifiedOptionalDependencyIsNullDuringInjectionAndLookup() {
        val consumer = Component(
            type = typeOf<OptionalConsumer>(),
            construct = { OptionalConsumer(it.single() as String?) },
            constructorArgs = listOf(Dependency.Single(typeOf<String>(), "missing", required = false)),
        )
        Context.instantiate(listOf(consumer, Component { -> "other" }.named("other"))).use { context ->
            assertNull(context.get<OptionalConsumer>().value)
            assertNull(context.getOrNull<String>("missing"))
            assertFailsWith<IllegalStateException> { context.get<String>("missing") }
        }
    }

    @Test
    fun optionalQualifiedDependencyStillRejectsAmbiguousProviders() {
        val first = Component { -> "first" }.named("duplicate")
        val second = Component { -> "second" }.named("duplicate")
        Context.instantiate(listOf(first, second)).use { context ->
            assertFailsWith<IllegalStateException> { context.getOrNull<String>("duplicate") }
        }
    }

    interface Event
    class FirstEvent : Event
    class SecondEvent : Event
    class Listener

    @Test
    fun closingContextDuringRefreshedEventDoesNotReturnClosedContext() {
        lateinit var constructingContext: Context
        var closeCount = 0
        val component = Component { context: Context -> constructingContext = context; Listener() }
            .listening<ContextRefreshedEvent> { event ->
                assertTrue(event.context.isStarted)
                event.context.close()
            }
            .onClose { closeCount++ }
        assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component)) }
        assertTrue(constructingContext.isClosed)
        assertTrue(constructingContext.instances.isEmpty())
        assertEquals(1, closeCount)
        constructingContext.close()
        assertEquals(1, closeCount)
    }

    @Test
    fun closingParentDuringChildRefreshedEventCleansUpChild() {
        val parent = Context.instantiate(emptyList())
        lateinit var child: Context
        var closeCount = 0
        val component = Component { context: Context -> child = context; Listener() }
            .listening<ContextRefreshedEvent> { parent.close() }
            .onClose { closeCount++ }
        parent.use {
            assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component), parent) }
            assertTrue(parent.isClosed)
            assertTrue(child.isClosed)
            assertTrue(child.instances.isEmpty())
            assertEquals(1, closeCount)
        }
    }

    @Test
    fun closingParentDuringChildConstructionClosesReturnedInstance() {
        val parent = Context.instantiate(emptyList())
        lateinit var child: Context
        var closeCount = 0
        val component = Component { context: Context ->
            child = context
            parent.close()
            Listener()
        }.onClose { closeCount++ }
        parent.use {
            assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component), parent) }
            assertTrue(child.isClosed)
            assertFalse(child.isStarted)
            assertTrue(child.instances.isEmpty())
            assertEquals(1, closeCount)
        }
    }

    @Test
    fun closingParentInLastChildPostConstructDoesNotCompleteStartup() {
        val parent = Context.instantiate(emptyList())
        lateinit var child: Context
        var closeCount = 0
        val component = Component { context: Context -> child = context; Listener() }
            .onInit { parent.close() }
            .onClose { closeCount++ }
        parent.use {
            assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component), parent) }
            assertTrue(child.isClosed)
            assertFalse(child.isStarted)
            assertTrue(child.instances.isEmpty())
            assertEquals(1, closeCount)
        }
    }

    @Test
    fun eventCacheDoesNotReuseRuntimeSubclassListenersForSameDeclaredType() {
        val received = mutableListOf<String>()
        val listener = Component { -> Listener() }
            .listening<FirstEvent> { received.add("first") }
            .listening<SecondEvent> { received.add("second") }
            .listening<Event> { received.add("base") }
        Context.instantiate(listOf(listener)).use { context ->
            context.publishEvent<Event>(FirstEvent())
            context.publishEvent<Event>(SecondEvent())
            context.publishEvent<Event>(FirstEvent())
            assertEquals(listOf("first", "base", "second", "base", "first", "base"), received)
        }
    }

    @Test
    fun resolverExceptionsAreReturnedAsFailures() {
        val failure = IllegalStateException("resolver unavailable")
        val resolver = ValueResolver.with { throw failure }
        val dependency = Dependency.parseValueExpression(TypeProxy<String>(), "\${key}")
        assertSame(failure, resolver.resolveValue(dependency).exceptionOrNull())
    }

    @Test
    fun closingContextInLastPostConstructDoesNotCompleteStartup() {
        lateinit var constructingContext: Context
        var closed = false
        val component = Component(
            type = typeOf<Listener>(),
            construct = { constructingContext = this; Listener() },
            constructorArgs = emptyList(),
            postConstruct = { constructingContext.close() },
            close = { closed = true },
        )
        assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component)) }
        assertTrue(closed)
        assertTrue(constructingContext.isClosed)
        assertFalse(constructingContext.isStarted)
    }

    @Test
    fun closingContextDuringConstructionStillClosesReturnedInstance() {
        lateinit var constructingContext: Context
        var closeCount = 0
        val cleanupFailure = AssertionError("cleanup failed")
        val component = Component(
            type = typeOf<Listener>(),
            construct = {
                constructingContext = this
                close()
                Listener()
            },
            constructorArgs = emptyList(),
            close = { closeCount++; throw cleanupFailure },
        )
        val failure = assertFailsWith<IllegalStateException> { Context.instantiate(listOf(component)) }
        assertEquals(1, closeCount)
        assertSame(cleanupFailure, failure.suppressedExceptions.single())
        assertTrue(constructingContext.instances.isEmpty())
        constructingContext.close()
        assertEquals(1, closeCount)
    }

    @Test
    fun allCloseCallbacksRunAndTheirFailuresAreRetained() {
        val calls = mutableListOf<Int>()
        val first = IllegalArgumentException("first")
        val second = IllegalStateException("second")
        val component = Component { -> Listener() }
            .onClose { calls.add(1); throw first }
            .onClose { calls.add(2); throw second }
            .onClose { calls.add(3) }
        val context = Context.instantiate(listOf(component))
        val failure = assertFailsWith<IllegalArgumentException> { context.close() }
        assertEquals(listOf(1, 2, 3), calls)
        assertSame(first, failure)
        assertSame(second, failure.suppressedExceptions.single())
        context.close()
        assertEquals(listOf(1, 2, 3), calls)
    }

    @Test
    fun constructorErrorClosesEarlierInstancesAndRetainsCleanupError() {
        val startupFailure = AssertionError("constructor failed")
        val cleanupFailure = AssertionError("cleanup failed")
        val calls = mutableListOf<String>()
        val resource = Component { -> Listener() }
            .onClose { calls.add("cleanup"); throw cleanupFailure }
            .onClose { calls.add("last cleanup") }
        val broken = Component(
            type = typeOf<OptionalConsumer>(),
            construct = { throw startupFailure },
            constructorArgs = listOf(Dependency.Single(typeOf<Listener>(), null, required = true)),
        )
        val failure = assertFailsWith<AssertionError> { Context.instantiate(listOf(resource, broken)) }
        assertSame(startupFailure, failure)
        assertSame(cleanupFailure, failure.suppressedExceptions.single())
        assertEquals(listOf("cleanup", "last cleanup"), calls)
    }

    @Test
    fun startupAndCleanupSharingSameErrorDoNotSelfSuppress() {
        val sharedFailure = AssertionError("shared failure")
        var closed = false
        val component = Component { -> Listener() }
            .onInit { throw sharedFailure }
            .onClose { closed = true; throw sharedFailure }
        val failure = assertFailsWith<AssertionError> { Context.instantiate(listOf(component)) }
        assertSame(sharedFailure, failure)
        assertTrue(failure.suppressedExceptions.isEmpty())
        assertTrue(closed)
    }

    @Test
    fun closeErrorsDoNotSkipCallbacksOrOtherComponentsOrSelfSuppress() {
        val firstFailure = AssertionError("first")
        val laterFailure = AssertionError("later")
        val calls = mutableListOf<Int>()
        val earliest = Component { -> Listener() }.named("earliest")
            .onClose { calls.add(0); throw laterFailure }
        val first = Component { -> Listener() }.named("first")
            .onClose { calls.add(1); throw firstFailure }
        val second = Component { -> Listener() }.named("second")
            .onClose { calls.add(2); throw firstFailure }
            .onClose { calls.add(3); throw firstFailure }
            .onClose { calls.add(4) }
        val context = Context.instantiate(listOf(earliest, first, second))
        val failure = assertFailsWith<AssertionError> { context.close() }
        assertSame(firstFailure, failure)
        assertEquals(listOf(2, 3, 4, 1, 0), calls)
        assertSame(laterFailure, failure.suppressedExceptions.single())
        assertTrue(context.instances.isEmpty())
        context.close()
        assertEquals(listOf(2, 3, 4, 1, 0), calls)
    }

    @Test
    fun closeEventErrorStillClosesResourcesWithoutSelfSuppression() {
        val sharedFailure = AssertionError("event and cleanup failed")
        val calls = mutableListOf<String>()
        val first = Component { -> Listener() }.named("first")
            .onClose { calls.add("first") }
        val second = Component { -> Listener() }.named("second")
            .listening<ContextClosedEvent> { throw sharedFailure }
            .onClose { calls.add("second"); throw sharedFailure }
        val context = Context.instantiate(listOf(first, second))
        val failure = assertFailsWith<AssertionError> { context.close() }
        assertSame(sharedFailure, failure)
        assertEquals(listOf("second", "first"), calls)
        assertTrue(failure.suppressedExceptions.isEmpty())
        assertTrue(context.instances.isEmpty())
    }

    @Test
    fun nullableDependencyReceivesPresentNonNullableProvider() {
        Context.instantiate(listOf(Component { -> "present" }, Component(::OptionalConsumer))).use { context ->
            assertEquals("present", context.get<OptionalConsumer>().value)
        }
    }

    @Test
    fun nullableSupertypeMatchingDoesNotEraseNestedNullability() {
        val component = Component { -> listOf("present") }
            .withSuperType<_, Collection<String>>()
        assertTrue(component.isSubtypeOf(typeOf<Any?>()))
        assertTrue(component.isSubtypeOf(typeOf<Collection<String>?>()))
        val invariant = Component { -> GenericValue("present") }
        assertTrue(invariant.isSubtypeOf(typeOf<GenericValue<String>?>()))
        assertFalse(invariant.isSubtypeOf(typeOf<GenericValue<String?>?>()))
    }
}
