package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QualifierTest {

    // Test components with different qualifiers
    @de.sfxr.mindi.annotations.Component("repository")
    @Qualifier("mysql")
    class MySqlRepository : TestRepository {
        override fun getData(): String = "MySQL data"
    }

    @de.sfxr.mindi.annotations.Component("repository")
    @Qualifier("postgres")
    class PostgresRepository : TestRepository {
        override fun getData(): String = "PostgreSQL data"
    }

    // Service with qualifier on constructor parameter
    class ConstructorService(
        @Qualifier("mysql")
        val repo: TestRepository
    )

    // Service with qualifier on setter method
    class SetterService {
        @Autowired
        @Qualifier("postgres")
        lateinit var repo: TestRepository
    }

    interface TestRepository {
        fun getData(): String
    }

    @Test
    fun testQualifierOnComponentDefinition() {
        // Create components using reflection with qualifiers
        val mysqlComponent = Reflector.Default.reflect(MySqlRepository::class)
        val postgresComponent = Reflector.Default.reflect(PostgresRepository::class)

        // Check both have the same component name but different qualifiers
        assertEquals("repository", mysqlComponent.name)
        assertEquals(1, mysqlComponent.qualifiers.size)
        assertEquals("mysql", mysqlComponent.qualifiers[0])

        assertEquals("repository", postgresComponent.name)
        assertEquals(1, postgresComponent.qualifiers.size)
        assertEquals("postgres", postgresComponent.qualifiers[0])
    }

    @Test
    fun testQualifierOnConstructorInjection() {
        val mysqlComponent = Reflector.Default.reflect(MySqlRepository::class)
        val postgresComponent = Reflector.Default.reflect(PostgresRepository::class)

        // Service component with qualified constructor dependency
        val serviceComponent = Reflector.Default.reflect(ConstructorService::class)

        // Create context and instantiate
        val plan = Plan.build(listOf(mysqlComponent, postgresComponent, serviceComponent))
        Context.instantiate(plan).use { context ->
            val constructorService = context.instances.filterIsInstance<ConstructorService>().first()
            assertIs<MySqlRepository>(constructorService.repo)
        }
    }

    @Test
    fun testQualifierOnSetterInjection() {
        val mysqlComponent = Reflector.Default.reflect(MySqlRepository::class)
        val postgresComponent = Reflector.Default.reflect(PostgresRepository::class)
        val serviceComponent = Reflector.Default.reflect(SetterService::class)

        // Create context and instantiate
        val plan = Plan.build(listOf(mysqlComponent, postgresComponent, serviceComponent))
        Context.instantiate(plan).use { context ->
            val setterService = context.instances.filterIsInstance<SetterService>().first()
            assertIs<PostgresRepository>(setterService.repo)
        }
    }
}
