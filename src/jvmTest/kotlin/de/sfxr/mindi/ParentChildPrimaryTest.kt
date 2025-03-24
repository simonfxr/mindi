package de.sfxr.mindi

import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.*

/**
 * Tests the interaction of parent-child contexts with primary and non-primary components.
 *
 * Key test cases:
 * 1. Child components take precedence over parent components of the same type
 * 2. Primary components in parent don't override non-primary components in child
 * 3. Non-primary components in parent can be overridden by non-primary components in child
 * 4. Components can be accessed specifically by qualifier across contexts
 */
class ParentChildPrimaryTest {

    // Common interface for test services
    interface TestService {
        fun getName(): String
    }

    // Parent primary implementation
    @Primary
    @Component
    class ParentPrimaryService : TestService {
        override fun getName(): String = "ParentPrimaryService"
    }

    // Parent non-primary implementation
    @Component
    @Qualifier("nonPrimary")
    class ParentNonPrimaryService : TestService {
        override fun getName(): String = "ParentNonPrimaryService"
    }

    // Child non-primary implementation
    @Component
    class ChildNonPrimaryService : TestService {
        override fun getName(): String = "ChildNonPrimaryService"
    }

    // Child primary implementation
    @Primary
    @Component
    class ChildPrimaryService : TestService {
        override fun getName(): String = "ChildPrimaryService"
    }

    // Service consumer in parent
    @Component
    class ParentServiceConsumer {
        @Autowired
        lateinit var service: TestService
    }

    // Service consumer in child
    @Component
    class ChildServiceConsumer {
        @Autowired
        lateinit var service: TestService
    }

    // Consumer that specifically wants parent service
    @Component
    class QualifiedParentConsumer {
        @Autowired
        @Qualifier("nonPrimary")
        lateinit var service: TestService
    }

    /**
     * Tests that a non-primary component in child context takes precedence over
     * a primary component in parent context.
     *
     * Child context components should always take precedence over parent context
     * components, regardless of primary status. This is the correct behavior for
     * most dependency injection systems.
     */
    @Test
    fun testChildNonPrimaryTakesPrecedenceOverParentPrimary() {
        // Create parent context with primary service
        val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with non-primary service and consumer
            val childService = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val childConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)
            val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get the consumer and check which service it's using
                val consumer = childContext.instances.filterIsInstance<ChildServiceConsumer>().first()
                assertNotNull(consumer.service)

                // After the fix, the child service should be used, not the parent primary
                assertIs<ChildNonPrimaryService>(consumer.service,
                    "Child non-primary service should take precedence over parent primary service")
                assertEquals("ChildNonPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that when both parent and child contexts have primary components,
     * the child's primary component is used because child components take precedence.
     */
    @Test
    fun testChildPrimaryTakesPrecedenceOverParentPrimary() {
        // Create parent context with primary service
        val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with primary service and consumer
            val childService = Reflector.Default.reflect(ChildPrimaryService::class)
            val childConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)
            val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get the consumer and check which service it's using
                val consumer = childContext.instances.filterIsInstance<ChildServiceConsumer>().first()
                assertNotNull(consumer.service)

                // Child's primary component should be used
                assertIs<ChildPrimaryService>(consumer.service,
                    "Child primary service should take precedence over parent primary service")
                assertEquals("ChildPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that a parent context consumer gets the parent primary component
     * even when a child context has its own component of the same type.
     *
     * This is because the parent's components are only resolved within the parent context,
     * without knowledge of the child context.
     */
    @Test
    fun testParentContextUsesParentPrimary() {
        // Create parent context with primary service and parent consumer
        val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentConsumer = Reflector.Default.reflect(ParentServiceConsumer::class)
        val parentPlan = Plan.build(listOf(parentService, parentConsumer))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with its own service implementation
            val childService = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val childPlan = Plan.build(listOf(childService), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Parent consumer should use the parent service
                val consumer = parentContext.instances.filterIsInstance<ParentServiceConsumer>().first()
                assertNotNull(consumer.service)

                // Should be the parent primary service
                assertIs<ParentPrimaryService>(consumer.service)
                assertEquals("ParentPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that qualified components can be accessed specifically even in parent-child hierarchy
     */
    @Test
    fun testQualifiedComponentsAcrossContexts() {
        // Create parent context with qualified non-primary service
        val parentService = Reflector.Default.reflect(ParentNonPrimaryService::class)
        val primaryParentService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentService, primaryParentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with non-qualified service and qualified consumer
            val childService = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val qualifiedConsumer = Reflector.Default.reflect(QualifiedParentConsumer::class)
            val childPlan = Plan.build(listOf(childService, qualifiedConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // The qualified consumer should get the qualified parent service
                val consumer = childContext.instances.filterIsInstance<QualifiedParentConsumer>().first()
                assertNotNull(consumer.service)

                // Should be the specifically qualified parent service
                assertIs<ParentNonPrimaryService>(consumer.service,
                    "Qualifier should allow accessing specific parent component even when child components exist")
                assertEquals("ParentNonPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that an ambiguity error occurs when multiple non-primary components of the same type
     * exist in the child context.
     */
    @Test
    fun testAmbiguityWithMultipleNonPrimaryComponents() {
        // Create parent context with no components
        val parentPlan = Plan.build(emptyList())
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with two non-primary services of the same type
            // This is a case where we should still get an ambiguity error

            // Create a second non-primary child service for ambiguity testing
            @de.sfxr.mindi.annotations.Component
            class SecondChildNonPrimaryService : TestService {
                override fun getName(): String = "SecondChildNonPrimaryService"
            }

            val childService1 = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val childService2 = Reflector.Default.reflect(SecondChildNonPrimaryService::class)
            val childServiceConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)

            // This should still fail with ambiguity because we have two non-primary components
            // of the same type in the child context
            val exception = assertFailsWith<IllegalStateException> {
                val childPlan = Plan.build(listOf(childService1, childService2, childServiceConsumer), parentPlan)
                Context.instantiate(childPlan, parentContext = parentContext)
            }

            println("*** ${exception.message}")
            assertTrue(exception.message?.contains("ambiguous") == true,
                "Exception should mention ambiguous providers")
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that when both parent and child contexts have non-primary components,
     * the child's component is used because child components take precedence.
     */
    @Test
    fun testChildNonPrimaryTakesPrecedenceOverParentNonPrimary() {
        // Create parent context with non-primary service
        val parentService = Reflector.Default.reflect(ParentNonPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with non-primary service and consumer
            val childService = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val childConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)
            val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get the consumer and check which service it's using
                val consumer = childContext.instances.filterIsInstance<ChildServiceConsumer>().first()
                assertNotNull(consumer.service)

                // Child's non-primary component should be used
                assertIs<ChildNonPrimaryService>(consumer.service,
                    "Child non-primary service should take precedence over parent non-primary service")
                assertEquals("ChildNonPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that when multiple components of the same type exist in the child context,
     * the primary one is chosen.
     */
    @Test
    fun testPrimaryComponentChosenFromMultipleComponents() {
        // Create parent context with both primary and non-primary services
        val parentPrimaryService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentNonPrimaryService = Reflector.Default.reflect(ParentNonPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentPrimaryService, parentNonPrimaryService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with both primary and non-primary services
            val childPrimaryService = Reflector.Default.reflect(ChildPrimaryService::class)
            val childNonPrimaryService = Reflector.Default.reflect(ChildNonPrimaryService::class)
            val childServiceConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)

            // Now with our fix, this should work by choosing the primary component
            val childPlan = Plan.build(listOf(childPrimaryService, childNonPrimaryService, childServiceConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get the consumer and check which service it's using
                val consumer = childContext.instances.filterIsInstance<ChildServiceConsumer>().first()
                assertNotNull(consumer.service)

                // Child's primary component should be used
                assertIs<ChildPrimaryService>(consumer.service)
                assertEquals("ChildPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }

    /**
     * Tests that a single primary component in the child context is selected
     * when available, even if the parent has components of the same type.
     */
    @Test
    fun testChildPrimaryComponentTakesPrecedence() {
        // Create parent context with both primary and non-primary services
        val parentPrimaryService = Reflector.Default.reflect(ParentPrimaryService::class)
        val parentNonPrimaryService = Reflector.Default.reflect(ParentNonPrimaryService::class)
        val parentPlan = Plan.build(listOf(parentPrimaryService, parentNonPrimaryService))
        val parentContext = Context.instantiate(parentPlan)

        try {
            // Create child context with only primary service
            val childPrimaryService = Reflector.Default.reflect(ChildPrimaryService::class)
            val childServiceConsumer = Reflector.Default.reflect(ChildServiceConsumer::class)
            val childPlan = Plan.build(listOf(childPrimaryService, childServiceConsumer), parentPlan)
            val childContext = Context.instantiate(childPlan, parentContext = parentContext)

            try {
                // Get the consumer and check which service it's using
                val consumer = childContext.instances.filterIsInstance<ChildServiceConsumer>().first()
                assertNotNull(consumer.service)

                // Child's primary component should be used
                assertIs<ChildPrimaryService>(consumer.service)
                assertEquals("ChildPrimaryService", consumer.service.getName())
            } finally {
                childContext.close()
            }
        } finally {
            parentContext.close()
        }
    }
}