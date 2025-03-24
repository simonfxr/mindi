package de.sfxr.mindi

import kotlin.test.*

/**
 * Tests dependency resolution across multiple levels of context hierarchy.
 *
 * This is the multiplatform equivalent of the JVM-specific MultiLevelContextTest.
 * It uses the pure Kotlin API instead of reflection and annotations.
 */
class MultiLevelContextApiTest {

    // Common interface for test services
    interface TestService {
        fun getName(): String
        fun getLevel(): Int
    }

    // Grandparent level service (level 1)
    class GrandparentService : TestService {
        override fun getName(): String = "GrandparentService"
        override fun getLevel(): Int = 1
    }

    // Parent level services (level 2)
    class ParentPrimaryService : TestService {
        override fun getName(): String = "ParentPrimaryService"
        override fun getLevel(): Int = 2
    }

    class ParentNonPrimaryService : TestService {
        override fun getName(): String = "ParentNonPrimaryService"
        override fun getLevel(): Int = 2
    }

    // Child level services (level 3)
    class ChildNonPrimaryService : TestService {
        override fun getName(): String = "ChildNonPrimaryService"
        override fun getLevel(): Int = 3
    }

    class ChildPrimaryService : TestService {
        override fun getName(): String = "ChildPrimaryService"
        override fun getLevel(): Int = 3
    }

    // Services specifically for qualified testing
    class QualifiedLevel1Service : TestService {
        override fun getName(): String = "QualifiedLevel1Service"
        override fun getLevel(): Int = 1
    }

    class QualifiedLevel2Service : TestService {
        override fun getName(): String = "QualifiedLevel2Service"
        override fun getLevel(): Int = 2
    }

    class QualifiedLevel3Service : TestService {
        override fun getName(): String = "QualifiedLevel3Service"
        override fun getLevel(): Int = 3
    }

    // Service consumers at different levels
    class GrandparentConsumer {
        // Using lateinit to match JVM version (@Autowired lateinit var)
        lateinit var service: TestService
    }

    class ParentConsumer {
        // Using lateinit to match JVM version (@Autowired lateinit var)
        lateinit var service: TestService
    }

    class ChildConsumer {
        // Using lateinit to match JVM version (@Autowired lateinit var)
        lateinit var service: TestService
    }

    // Consumer with specific qualifiers
    class QualifiedConsumer {
        // Using lateinit to match JVM version (@Autowired @Qualifier("level1") lateinit var)
        lateinit var level1Service: TestService
        lateinit var level2Service: TestService
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
        val grandparentService = Component { -> GrandparentService() }
            .withSuperType<_, TestService>()
            .with(primary = true)
            .named("grandparentService")

        val grandparentConsumer = Component { -> GrandparentConsumer() }
            .setting(required = true) { it: TestService -> this.service = it }
            .named("grandparentConsumer")

        val grandparentPlan = Plan.build(listOf(grandparentService, grandparentConsumer))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with its service
            val parentService = Component { -> ParentPrimaryService() }
                .withSuperType<_, TestService>()
                .with(primary = true)
                .named("parentService")

            val parentConsumer = Component { -> ParentConsumer() }
                .setting(required = true) { it: TestService -> this.service = it }
                .named("parentConsumer")

            val parentPlan = Plan.build(listOf(parentService, parentConsumer), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with its service
                val childService = Component { -> ChildPrimaryService() }
                    .withSuperType<_, TestService>()
                    .with(primary = true)
                    .named("childService")

                val childConsumer = Component { -> ChildConsumer() }
                    .setting(required = true) { it: TestService -> this.service = it }
                    .named("childConsumer")

                val childPlan = Plan.build(listOf(childService, childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Grandparent consumer should get grandparent service
                    val gpConsumer = grandparentContext.instances.filterIsInstance<GrandparentConsumer>().first()
                    assertNotNull(gpConsumer.service)
                    assertIs<GrandparentService>(gpConsumer.service)
                    assertEquals("GrandparentService", gpConsumer.service.getName())
                    assertEquals(1, gpConsumer.service.getLevel())

                    // Parent consumer should get parent service
                    val pConsumer = parentContext.instances.filterIsInstance<ParentConsumer>().first()
                    assertNotNull(pConsumer.service)
                    assertIs<ParentPrimaryService>(pConsumer.service)
                    assertEquals("ParentPrimaryService", pConsumer.service.getName())
                    assertEquals(2, pConsumer.service.getLevel())

                    // Child consumer should get child service
                    val cConsumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertNotNull(cConsumer.service)
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
        val grandparentService = Component { -> GrandparentService() }
            .withSuperType<_, TestService>()
            .with(primary = true)
            .named("grandparentService")

        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with primary service
            val parentService = Component { -> ParentPrimaryService() }
                .withSuperType<_, TestService>()
                .with(primary = true)
                .named("parentService")

            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with non-primary service and consumer
                val childService = Component { -> ChildNonPrimaryService() }
                    .withSuperType<_, TestService>()
                    .with(primary = false)
                    .named("childService")

                val childConsumer = Component { -> ChildConsumer() }
                    .setting(required = true) { it: TestService -> this.service = it }
                    .named("childConsumer")

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
        val grandparentService = Component { -> GrandparentService() }
            .withSuperType<_, TestService>()
            .with(primary = true)
            .named("grandparentService")

        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with non-primary service
            val parentService = Component { -> ParentNonPrimaryService() }
                .withSuperType<_, TestService>()
                .with(primary = false)
                .named("parentNonPrimaryService")
                .named("nonPrimary")

            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with consumer but no service
                val childConsumer = Component { -> ChildConsumer() }
                    .setting(required = true) { it: TestService -> this.service = it }
                    .named("childConsumer")

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
        val level1Service = Component { -> QualifiedLevel1Service() }
            .withSuperType<_, TestService>()
            .named("level1")

        val grandparentPlan = Plan.build(listOf(level1Service))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with qualified level2 service
            val level2Service = Component { -> QualifiedLevel2Service() }
                .withSuperType<_, TestService>()
                .named("level2")

            val parentPlan = Plan.build(listOf(level2Service), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with qualified level3 service and qualified consumer
                val level3Service = Component { -> QualifiedLevel3Service() }
                    .withSuperType<_, TestService>()
                    .named("level3")

                val qualifiedConsumer = Component { -> QualifiedConsumer() }
                    .setting(required = true, qualifier = "level1") { it: TestService -> this.level1Service = it }
                    .setting(required = true, qualifier = "level2") { it: TestService -> this.level2Service = it }
                    .setting(required = true, qualifier = "level3") { it: TestService -> this.level3Service = it }
                    .named("qualifiedConsumer")

                val childPlan = Plan.build(listOf(level3Service, qualifiedConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Get the qualified consumer and check it has all services
                    val consumer = childContext.instances.filterIsInstance<QualifiedConsumer>().first()

                    // Should get the level1 service from grandparent context
                    assertNotNull(consumer.level1Service)
                    assertIs<QualifiedLevel1Service>(consumer.level1Service)
                    assertEquals("QualifiedLevel1Service", consumer.level1Service.getName())
                    assertEquals(1, consumer.level1Service.getLevel())

                    // Should get the level2 service from parent context
                    assertNotNull(consumer.level2Service)
                    assertIs<QualifiedLevel2Service>(consumer.level2Service)
                    assertEquals("QualifiedLevel2Service", consumer.level2Service.getName())
                    assertEquals(2, consumer.level2Service.getLevel())

                    // Should get the level3 service from child context
                    assertNotNull(consumer.level3Service)
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
        val grandparentService = Component { -> GrandparentService() }
            .withSuperType<_, TestService>()
            .with(primary = true)
            .named("grandparentService")

        val grandparentPlan = Plan.build(listOf(grandparentService))
        val grandparentContext = Context.instantiate(grandparentPlan)

        try {
            // Create parent context with primary service
            val parentService = Component { -> ParentPrimaryService() }
                .withSuperType<_, TestService>()
                .with(primary = true)
                .named("parentService")

            val parentPlan = Plan.build(listOf(parentService), grandparentPlan)
            val parentContext = Context.instantiate(parentPlan, parentContext = grandparentContext)

            try {
                // Create child context with only a consumer
                // Add a qualifier to specifically request the parent service
                val childConsumer = Component { -> ChildConsumer() }
                    .setting(qualifier = "parentService", required = true) { it: TestService -> this.service = it }
                    .named("childConsumer")

                val childPlan = Plan.build(listOf(childConsumer), parentPlan)
                val childContext = Context.instantiate(childPlan, parentContext = parentContext)

                try {
                    // Child consumer should get parent service
                    val consumer = childContext.instances.filterIsInstance<ChildConsumer>().first()
                    assertNotNull(consumer.service)

                    // The parent primary service should be used, not the grandparent primary
                    val service = consumer.service
                    assertNotNull(service, "Service should not be null")
                    assertTrue(service is ParentPrimaryService, "Service should be ParentPrimaryService but was ${service::class.simpleName}")
                    assertEquals("ParentPrimaryService", service.getName())
                    assertEquals(2, service.getLevel())
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
        class Level1Service : TestService {
            override fun getName(): String = "Level1Service"
            override fun getLevel(): Int = 1
        }

        val level1Service = Component { -> Level1Service() }
            .withSuperType<_, TestService>()
            .named("level1Service")

        val level1Plan = Plan.build(listOf(level1Service))
        val level1Context = Context.instantiate(level1Plan)

        try {
            // Create level 2 (grandparent) context
            class Level2Service : TestService {
                override fun getName(): String = "Level2Service"
                override fun getLevel(): Int = 2
            }

            val level2Service = Component { -> Level2Service() }
                .withSuperType<_, TestService>()
                .named("level2Service")

            val level2Plan = Plan.build(listOf(level2Service), level1Plan)
            val level2Context = Context.instantiate(level2Plan, parentContext = level1Context)

            try {
                // Create level 3 (parent) context
                class Level3Service : TestService {
                    override fun getName(): String = "Level3Service"
                    override fun getLevel(): Int = 3
                }

                val level3Service = Component { -> Level3Service() }
                    .withSuperType<_, TestService>()
                    .named("level3Service")

                val level3Plan = Plan.build(listOf(level3Service), level2Plan)
                val level3Context = Context.instantiate(level3Plan, parentContext = level2Context)

                try {
                    // Create level 4 (child) context with consumer
                    class Level4Service : TestService {
                        override fun getName(): String = "Level4Service"
                        override fun getLevel(): Int = 4
                    }

                    val level4Service = Component { -> Level4Service() }
                        .withSuperType<_, TestService>()
                        .named("level4Service")

                    val childConsumer = Component { -> ChildConsumer() }
                        .setting(required = true) { it: TestService -> this.service = it }
                        .named("childConsumer")

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