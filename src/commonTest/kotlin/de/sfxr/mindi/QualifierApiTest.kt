package de.sfxr.mindi

import kotlin.test.*

/**
 * Tests for qualifier functionality using the pure Kotlin API.
 *
 * This is the multiplatform equivalent of the JVM-specific QualifierTest.
 */
class QualifierApiTest {

    // Test components with different qualifiers
    class MySqlRepository : TestRepository {
        override fun getData(): String = "MySQL data"
    }

    class PostgresRepository : TestRepository {
        override fun getData(): String = "PostgreSQL data"
    }

    // Service with qualified dependency
    class ConstructorService(
        val repo: TestRepository
    )

    // Service with setter dependency
    class SetterService {
        // Using lateinit to match JVM version (@Autowired lateinit var)
        lateinit var repo: TestRepository
    }

    interface TestRepository {
        fun getData(): String
    }

    @Test
    fun testQualifierOnComponentDefinition() {
        // Create components with multiple names (the secondary name acts as qualifier)
        val mysqlComponent = Component { -> MySqlRepository() }
            .withSuperType<_, TestRepository>()
            .named("repository")
            .named("mysql")

        val postgresComponent = Component { -> PostgresRepository() }
            .withSuperType<_, TestRepository>()
            .named("repository")
            .named("postgres")

        // Check both have the same component name but different qualifiers
        val expectedNames = listOf("repository", "mysql")
        assertEquals(expectedNames.size, mysqlComponent.names.size)
        assertTrue(mysqlComponent.names.containsAll(expectedNames))

        val expectedPostgresNames = listOf("repository", "postgres")
        assertEquals(expectedPostgresNames.size, postgresComponent.names.size)
        assertTrue(postgresComponent.names.containsAll(expectedPostgresNames))
    }

    @Test
    fun testQualifierOnConstructorInjection() {
        // Create repository components with qualifiers
        val mysqlComponent = Component { -> MySqlRepository() }
            .withSuperType<_, TestRepository>()
            .named("mysql")

        val postgresComponent = Component { -> PostgresRepository() }
            .withSuperType<_, TestRepository>()
            .named("postgres")

        // Service component with qualified constructor dependency
        val serviceComponent = Component { repoParam: TestRepository ->
                ConstructorService(repoParam)
            }
            .requireQualified(0, "mysql")
            .named("constructorService")

        // Create context and instantiate
        val plan = Plan.build(listOf(mysqlComponent, postgresComponent, serviceComponent))
        Context.instantiate(plan).use { context ->
            val constructorService = context.instances.filterIsInstance<ConstructorService>().first()
            assertIs<MySqlRepository>(constructorService.repo)
            assertEquals("MySQL data", constructorService.repo.getData())
        }
    }

    @Test
    fun testQualifierOnSetterInjection() {
        // Create repository components with qualifiers
        val mysqlComponent = Component { -> MySqlRepository() }
            .withSuperType<_, TestRepository>()
            .named("mysql")

        val postgresComponent = Component { -> PostgresRepository() }
            .withSuperType<_, TestRepository>()
            .named("postgres")

        // Service component with qualified setter dependency
        val serviceComponent = Component { -> SetterService() }
            .setting(qualifier = "postgres", required = true) { it: TestRepository -> this.repo = it }
            .named("setterService")

        // Create context and instantiate
        val plan = Plan.build(listOf(mysqlComponent, postgresComponent, serviceComponent))
        Context.instantiate(plan).use { context ->
            val setterService = context.instances.filterIsInstance<SetterService>().first()
            assertIs<PostgresRepository>(setterService.repo)
            assertEquals("PostgreSQL data", setterService.repo.getData())
        }
    }
}