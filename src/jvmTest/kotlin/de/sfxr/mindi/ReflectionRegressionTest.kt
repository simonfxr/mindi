package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.Bean
import de.sfxr.mindi.annotations.PostConstruct
import de.sfxr.mindi.annotations.PreDestroy
import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import de.sfxr.mindi.reflect.reflectFactory
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectionRegressionTest {
    class NullableDependency<T : Any>(val value: T?)
    class NestedNullableDependency<T : Any>(val value: List<T?>)

    private class HiddenComponent {
        var initialized = false

        @PostConstruct
        fun initialize() {
            initialized = true
        }
    }

    private class HiddenFactory {
        @Bean
        fun service(): Service = SelectedService()
    }

    open class FailingCleanup {
        val calls = mutableListOf<String>()

        @PreDestroy
        fun destroyParent() {
            calls += "parent"
            error("parent cleanup failed")
        }
    }

    class CleanupComponent : FailingCleanup(), AutoCloseable {
        @PreDestroy
        fun destroyChild() {
            calls += "child"
            error("child cleanup failed")
        }

        override fun close() {
            calls += "close"
        }
    }

    open class AnnotatedCloseParent {
        val calls = mutableListOf<String>()

        @PreDestroy
        open fun close() {
            calls += "close"
            error("close failed")
        }

        @PreDestroy
        fun destroyParent() {
            calls += "parent"
        }
    }

    class AnnotatedCloseComponent : AnnotatedCloseParent(), AutoCloseable {
        @PreDestroy
        override fun close() = super.close()

        @PreDestroy
        fun destroyChild() {
            calls += "child"
        }
    }

    @Qualifier
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
    annotation class Selected

    interface Service
    @Selected
    class SelectedService : Service
    class OtherService : Service

    class FieldConsumer {
        @Autowired
        @field:Selected
        lateinit var service: Service
    }

    class SetterConsumer {
        var received: Service? = null

        @Autowired
        @Selected
        fun setService(service: Service) {
            received = service
        }
    }

    class AmbiguousConstructorConsumer(@Qualifier("named") @Selected val service: Service)

    class AmbiguousFieldConsumer {
        @Autowired
        @field:Qualifier("named")
        @field:Selected
        lateinit var service: Service
    }

    class SplitQualifierConsumer {
        @Autowired
        @Qualifier("named")
        @field:Selected
        lateinit var service: Service
    }

    class AmbiguousSetterConsumer {
        @Autowired
        @Qualifier("named")
        @Selected
        fun setService(service: Service) {}
    }

    class SplitSetterQualifierConsumer {
        @Autowired
        @Qualifier("named")
        fun setService(@Selected service: Service) {}
    }

    class RepeatedQualifierConsumer {
        @Autowired
        @Qualifier("named")
        @field:Qualifier("named")
        lateinit var service: Service
    }

    @Qualifier("named")
    @Selected
    class MultiplyQualifiedService : Service

    @Test
    fun injectionPointsRejectMultipleDistinctQualifiers() {
        for (klass in listOf(
            AmbiguousConstructorConsumer::class,
            AmbiguousFieldConsumer::class,
            SplitQualifierConsumer::class,
            AmbiguousSetterConsumer::class,
            SplitSetterQualifierConsumer::class,
        )) {
            val failure = assertFailsWith<IllegalArgumentException> { Reflector.Default.reflect(klass) }
            assertTrue(failure.message.orEmpty().contains("Multiple qualifiers"), "$klass: ${failure.message}")
            assertTrue(failure.message.orEmpty().contains("service", ignoreCase = true), "$klass: ${failure.message}")
        }
    }

    @Test
    fun repeatedQualifierAcrossAnnotationSitesIsAllowed() {
        val component = Reflector.Default.reflect<RepeatedQualifierConsumer>()
        assertEquals("named", (component.fields.single() as Dependency.Single).qualifier)
    }

    @Test
    fun providersCanStillExposeMultipleQualifiers() {
        val component = Reflector.Default.reflect<MultiplyQualifiedService>()
        assertEquals(2, component.qualifiers.size)
        assertTrue(component.isQualifiedBy("named"))
        assertTrue(component.qualifiers.any { it is Selected })
    }

    @Test
    fun nullableTypeParameterRemainsOptional() {
        val component = Reflector.Default.reflect<NullableDependency<String>>()
        assertEquals(typeOf<String?>(), component.constructorArgs.single().type)
        assertFalse(component.constructorArgs.single().required)
        Context.instantiate(Plan.build(listOf(component))).use { context ->
            assertNull(context.instances.filterIsInstance<NullableDependency<*>>().single().value)
        }
    }

    @Test
    fun nullableTypeParameterAcceptsPresentNonNullableProvider() {
        val components = listOf(
            Reflector.Default.reflect<NullableDependency<Service>>(),
            Reflector.Default.reflect<SelectedService>(),
        )
        Context.instantiate(Plan.build(components)).use { context ->
            assertIs<SelectedService>(context.instances.filterIsInstance<NullableDependency<*>>().single().value)
        }
    }

    @Test
    fun nestedNullableTypeParameterRetainsNullability() {
        val component = Reflector.Default.reflect<NestedNullableDependency<String>>()
        // Collection dependencies record the element type, not the collection type.
        assertEquals(typeOf<String?>(), component.constructorArgs.single().type)
    }

    @Test
    fun fieldInjectionHonorsCustomQualifier() {
        val components = listOf(
            Reflector.Default.reflect<SelectedService>(),
            Reflector.Default.reflect<OtherService>(),
            Reflector.Default.reflect<FieldConsumer>(),
        )
        Context.instantiate(Plan.build(components)).use { context ->
            assertIs<SelectedService>(context.instances.filterIsInstance<FieldConsumer>().single().service)
        }
    }

    @Test
    fun setterInjectionHonorsCustomQualifier() {
        val components = listOf(
            Reflector.Default.reflect<SelectedService>(),
            Reflector.Default.reflect<OtherService>(),
            Reflector.Default.reflect<SetterConsumer>(),
        )
        Context.instantiate(Plan.build(components)).use { context ->
            assertIs<SelectedService>(context.instances.filterIsInstance<SetterConsumer>().single().received)
        }
    }

    @Test
    fun publicMembersOfNonPublicComponentAreAccessible() {
        val component = Reflector.Default.reflect<HiddenComponent>()
        Context.instantiate(Plan.build(listOf(component))).use { context ->
            assertTrue(context.instances.filterIsInstance<HiddenComponent>().single().initialized)
        }
    }

    @Test
    fun publicMembersOfNonPublicFactoryAreAccessible() {
        val components = Reflector.Default.reflectFactory<HiddenFactory>()
        Context.instantiate(Plan.build(components)).use { context ->
            assertIs<SelectedService>(context.instances.filterIsInstance<Service>().single())
        }
    }

    @Test
    fun failingPreDestroyDoesNotSkipOtherCleanup() {
        val component = Reflector.Default.reflect<CleanupComponent>()
        val instance = CleanupComponent()
        val failure = assertFails { component.close!!(instance) }
        assertEquals(listOf("child", "parent", "close"), instance.calls)
        assertEquals("child cleanup failed", failure.cause?.message ?: failure.message)
        assertEquals(1, failure.suppressedExceptions.size)
    }

    @Test
    fun annotatedAutoCloseableCloseRunsOnceAndLastEvenWhenItFails() {
        val component = Reflector.Default.reflect<AnnotatedCloseComponent>()
        val instance = AnnotatedCloseComponent()
        val failure = assertFails { component.close!!(instance) }
        assertEquals(listOf("child", "parent", "close"), instance.calls)
        assertEquals("close failed", failure.message)
        assertTrue(failure.suppressedExceptions.isEmpty())
    }
}
