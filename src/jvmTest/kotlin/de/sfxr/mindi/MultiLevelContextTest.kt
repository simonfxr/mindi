package de.sfxr.mindi

import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.*

/**
 * Tests dependency resolution across multiple levels of context hierarchy.
 *
 * This test class focuses specifically on how components are resolved when
 * there are three or more levels of context (grandparent -> parent -> child),
 * verifying that components in closer contexts take precedence over those
 * in more distant contexts.
 */
class MultiLevelContextTest {

    // Common interface for test services
    interface TestService {
        fun getName(): String
        fun getLevel(): Int
    }

    // Grandparent level service (level 1)
    @Primary
    @Component
    class GrandparentService : TestService {
        override fun getName(): String = "GrandparentService"
        override fun getLevel(): Int = 1
    }

    // Parent level services (level 2)
    @Primary
    @Component
    class ParentPrimaryService : TestService {
        override fun getName(): String = "ParentPrimaryService"
        override fun getLevel(): Int = 2
    }

    @Component
    @Qualifier("nonPrimary")
    class ParentNonPrimaryService : TestService {
        override fun getName(): String = "ParentNonPrimaryService"
        override fun getLevel(): Int = 2
    }

    // Child level services (level 3)
    @Component
    class ChildNonPrimaryService : TestService {
        override fun getName(): String = "ChildNonPrimaryService"
        override fun getLevel(): Int = 3
    }

    @Primary
    @Component
    class ChildPrimaryService : TestService {
        override fun getName(): String = "ChildPrimaryService"
        override fun getLevel(): Int = 3
    }

    // Services specifically for qualified testing
    @Component
    @Qualifier("level1")
    class QualifiedLevel1Service : TestService {
        override fun getName(): String = "QualifiedLevel1Service"
        override fun getLevel(): Int = 1
    }

    @Component
    @Qualifier("level2")
    class QualifiedLevel2Service : TestService {
        override fun getName(): String = "QualifiedLevel2Service"
        override fun getLevel(): Int = 2
    }

    @Component
    @Qualifier("level3")
    class QualifiedLevel3Service : TestService {
        override fun getName(): String = "QualifiedLevel3Service"
        override fun getLevel(): Int = 3
    }

    // Service consumers at different levels
    @Component
    class GrandparentConsumer {
        @Autowired
        lateinit var service: TestService
    }

    @Component
    class ParentConsumer {
        @Autowired
        lateinit var service: TestService
    }

    @Component
    class ChildConsumer {
        @Autowired
        lateinit var service: TestService
    }

    // Consumer with specific qualifiers
    @Component
    class QualifiedConsumer {
        @Autowired
        @Qualifier("level1")
        lateinit var level1Service: TestService

        @Autowired
        @Qualifier("level2")
        lateinit var level2Service: TestService

        @Autowired
        @Qualifier("level3")
        lateinit var level3Service: TestService
    }

    /**
     * Tests a three-level context hierarchy where each level has its own component.
     * Verifies that each context's consumer gets the appropriate component based on
     * the closest available service according to the shadow rules.
     */
    @Test
    fun testBasicThreeLevelHierarchy() {
        // Create grandparent context with its service
        val grandparentService = Reflector.Default.reflect(GrandparentService::class)
        val grandparentConsumer = Reflector.Default.reflect(GrandparentConsumer::class)
        val grandparentPlan = Plan.build(listOf(grandparentService, grandparentConsumer))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with its service
            val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
            val parentConsumer = Reflector.Default.reflect(ParentConsumer::class)
            val parentPlan = Plan.build(listOf(parentService, parentConsumer), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with its service
                val childService = Reflector.Default.reflect(ChildPrimaryService::class)
                val childConsumer = Reflector.Default.reflect(ChildConsumer::class)
                val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Grandparent consumer should get grandparent service
                    val gpConsumer = grandparentContext.instances.filterIsInstance<GrandparentConsumer>().first()
                    assertIs<GrandparentService>(gpConsumer.service)
                    assertEquals("GrandparentService", gpConsumer.service.getName())
                    assertEquals(1, gpConsumer.service.getLevel())

                    // Parent consumer should get parent service
                    val pConsumer = parentContext.instances.filterIsInstance<ParentConsumer>().first()
                    assertIs<ParentPrimaryService>(pConsumer.service)
                    assertEquals("ParentPrimaryService", pConsumer.service.getName())
                    assertEquals(2, pConsumer.service.getLevel())

                    // Child consumer should get child service
                    val cConsumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertIs<ChildPrimaryService>(cConsumer.service)
                    assertEquals("ChildPrimaryService", cConsumer.service.getName())
                    assertEquals(3, cConsumer.service.getLevel())
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

    /**
     * Tests that child non-primary components take precedence over parent and grandparent
     * primary components in a three-level hierarchy.
     */
    @Test
    fun testChildNonPrimaryTakesPrecedenceInThreeLevels() {
        // Create grandparent context with primary service
        val grandparentService = Reflector.Default.reflect(GrandparentService::class)
        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with primary service
            val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with non-primary service and consumer
                val childService = Reflector.Default.reflect(ChildNonPrimaryService::class)
                val childConsumer = Reflector.Default.reflect(ChildConsumer::class)
                val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Child consumer should get child service
                    val consumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertNotNull(consumer.service)

                    // The child service should be used, not the parent or grandparent primary
                    assertIs<ChildNonPrimaryService>(consumer.service)
                    assertEquals("ChildNonPrimaryService", consumer.service.getName())
                    assertEquals(3, consumer.service.getLevel())
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

    /**
     * Tests that parent non-primary components take precedence over grandparent
     * primary components when child has no matching components.
     */
    @Test
    fun testParentNonPrimaryTakesPrecedenceOverGrandparentPrimary() {
        // Create grandparent context with primary service
        val grandparentService = Reflector.Default.reflect(GrandparentService::class)
        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with non-primary service
            val parentService = Reflector.Default.reflect(ParentNonPrimaryService::class)
            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with consumer but no service
                val childConsumer = Reflector.Default.reflect(ChildConsumer::class)
                val childPlan = Plan.build(listOf(childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Child consumer should get parent service
                    val consumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertNotNull(consumer.service)

                    // The parent non-primary service should be used, not the grandparent primary
                    assertIs<ParentNonPrimaryService>(consumer.service)
                    assertEquals("ParentNonPrimaryService", consumer.service.getName())
                    assertEquals(2, consumer.service.getLevel())
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

    /**
     * Tests access to specific qualified components across all three levels
     * of the context hierarchy using qualifiers.
     */
    @Test
    fun testQualifiedComponentsAcrossThreeLevels() {
        // Create grandparent context with qualified level1 service
        val level1Service = Reflector.Default.reflect(QualifiedLevel1Service::class)
        val grandparentPlan = Plan.build(listOf(level1Service))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with qualified level2 service
            val level2Service = Reflector.Default.reflect(QualifiedLevel2Service::class)
            val parentPlan = Plan.build(listOf(level2Service), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with qualified level3 service and qualified consumer
                val level3Service = Reflector.Default.reflect(QualifiedLevel3Service::class)
                val qualifiedConsumer = Reflector.Default.reflect(QualifiedConsumer::class)
                val childPlan = Plan.build(listOf(level3Service, qualifiedConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Get the qualified consumer and check it has all services
                    val consumer = childContext.instances.filterIsInstance<QualifiedConsumer>().first()

                    // Should get the level1 service from grandparent context
                    assertIs<QualifiedLevel1Service>(consumer.level1Service)
                    assertEquals("QualifiedLevel1Service", consumer.level1Service.getName())
                    assertEquals(1, consumer.level1Service.getLevel())

                    // Should get the level2 service from parent context
                    assertIs<QualifiedLevel2Service>(consumer.level2Service)
                    assertEquals("QualifiedLevel2Service", consumer.level2Service.getName())
                    assertEquals(2, consumer.level2Service.getLevel())

                    // Should get the level3 service from child context
                    assertIs<QualifiedLevel3Service>(consumer.level3Service)
                    assertEquals("QualifiedLevel3Service", consumer.level3Service.getName())
                    assertEquals(3, consumer.level3Service.getLevel())
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

    /**
     * Tests that when a context in the middle of the hierarchy has a primary component,
     * it takes precedence over a component in a higher context but is shadowed by
     * a component in a lower context.
     */
    @Test
    fun testPrimaryComponentInMiddleContext() {
        // Create grandparent context with primary service
        val grandparentService = Reflector.Default.reflect(GrandparentService::class)
        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with primary service
            val parentService = Reflector.Default.reflect(ParentPrimaryService::class)
            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with only a consumer
                val childConsumer = Reflector.Default.reflect(ChildConsumer::class)
                val childPlan = Plan.build(listOf(childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Child consumer should get parent service
                    val consumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertNotNull(consumer.service)

                    // The parent primary service should be used, not the grandparent primary
                    assertIs<ParentPrimaryService>(consumer.service)
                    assertEquals("ParentPrimaryService", consumer.service.getName())
                    assertEquals(2, consumer.service.getLevel())
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

    /**
     * Tests a more complex scenario with four levels of context hierarchy.
     */
    @Test
    fun testFourLevelContextHierarchy() {
        // Create level 1 (great-grandparent) context
        @Component
        class Level1Service : TestService {
            override fun getName(): String = "Level1Service"
            override fun getLevel(): Int = 1
        }

        val level1Service = Reflector.Default.reflect(Level1Service::class)
        val level1Plan = Plan.build(listOf(level1Service))
        val level1Context = Context.instantiate(level1Plan)

        try {
            // Create level 2 (grandparent) context
            @Component
            class Level2Service : TestService {
                override fun getName(): String = "Level2Service"
                override fun getLevel(): Int = 2
            }

            val level2Service = Reflector.Default.reflect(Level2Service::class)
            val level2Plan = Plan.build(listOf(level2Service), level1Plan)
            val level2Context = Context.instantiate(level2Plan, parentContext = level1Context)

            try {
                // Create level 3 (parent) context
                @Component
                class Level3Service : TestService {
                    override fun getName(): String = "Level3Service"
                    override fun getLevel(): Int = 3
                }

                val level3Service = Reflector.Default.reflect(Level3Service::class)
                val level3Plan = Plan.build(listOf(level3Service), level2Plan)
                val level3Context = Context.instantiate(level3Plan, parentContext = level2Context)

                try {
                    // Create level 4 (child) context with consumer
                    @Component
                    class Level4Service : TestService {
                        override fun getName(): String = "Level4Service"
                        override fun getLevel(): Int = 4
                    }

                    val level4Service = Reflector.Default.reflect(Level4Service::class)
                    val childConsumer = Reflector.Default.reflect(ChildConsumer::class)
                    val level4Plan = Plan.build(listOf(level4Service, childConsumer), level3Plan)
                    val level4Context = Context.instantiate(level4Plan, parentContext = level3Context)

                    try {
                        // Child consumer should get the level 4 service
                        val consumer = level4Context.instances.filterIsInstance<ChildConsumer>().first()
                        assertNotNull(consumer.service)

                        // The level 4 service should be used
                        assertIs<Level4Service>(consumer.service)
                        assertEquals("Level4Service", consumer.service.getName())
                        assertEquals(4, consumer.service.getLevel())
                    } finally {
                        level4Context.close()
                    }
                } finally {
                    level3Context.close()
                }
            } finally {
                level2Context.close()
            }
        } finally {
            level1Context.close()
        }
    }
}