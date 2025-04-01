# mindi - Minimal Dependency Injection for Kotlin

![JVM Tests Status](https://img.shields.io/badge/jvm%20tests-passing-brightgreen)
![Native Tests Status](https://img.shields.io/badge/native%20tests-passing-brightgreen)

mindi is a lightweight, flexible dependency injection framework for Kotlin Multiplatform projects. It provides a powerful DI container with Spring-like features while maintaining a small footprint and Kotlin-first design.

The framework's standout feature is its static dependency resolution system, which fully validates component graphs before instantiation. This ensures your application either starts completely or fails fast with clear error messages - no more partial initialization failures or runtime dependency surprises.

## Features

- **Static Dependency Resolution**: All dependencies are fully resolved statically before any constructors are called, preventing partial initialization failures
- **Fail-Fast Validation**: Build and validate component graphs at application startup, catching configuration errors early
- **Multiplatform Support**: Works on JVM, Native, and JS
- **Annotation-based Component Scanning**: Automatic discovery of components using annotations (JVM)
- **@Bean Factory Methods**: Define beans programmatically in configuration objects
- **Functional API**: Define components using Kotlin's type-safe DSL
- **Hierarchical Contexts**: Parent-child relationships for modular applications
- **Value Resolution**: Environment variable and property substitution
- **Lifecycle Management**: PostConstruct/PreDestroy hooks and automatic resource cleanup
- **AutoCloseable Support**: Components implementing AutoCloseable are automatically closed when the context is closed
- **Event System**: Publish-subscribe pattern with type-safe event handlers
- **Type-safe Dependency Resolution**: Autowire by type with generics support
- **Qualifier Support**: Disambiguate multiple implementations of the same interface
- **Primary Components**: Designate default implementations
- **Optional Dependencies**: Graceful handling of missing dependencies

## Getting Started

Add mindi to your project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("de.sfxr:mindi:0.1.0")
}
```

## Common API Example

The functional API allows you to define components and their dependencies explicitly:

```kotlin
import de.sfxr.mindi.*
import kotlin.reflect.typeOf

// Define your service interfaces and implementations
interface UserRepository {
    fun findById(id: String): User?
}

interface UserService {
    fun getUser(id: String): User?
}

class InMemoryUserRepository : UserRepository {
    private val users = mapOf("1" to User("1", "Alice"))

    override fun findById(id: String): User? = users[id]
}

class DefaultUserService(private val repository: UserRepository) : UserService {
    override fun getUser(id: String): User? = repository.findById(id)
}

// Define data classes
data class User(val id: String, val name: String)
data class AppConfig(val enableCaching: Boolean)

// Create a component definition for repository
val repositoryComponent = Component { -> InMemoryUserRepository() }
    .withSuperType<_, UserRepository>()  // Register as UserRepository type
    .named("userRepository")             // Give it a name

// Create a component that depends on the repository
val serviceComponent = Component { repo: UserRepository ->
    DefaultUserService(repo)
}
    .withSuperType<_, UserService>()  // Register as UserService type
    .named("userService")             // Give it a name

// Create a configuration component with injected environment values
val configComponent = Component { enableCaching: Boolean -> AppConfig(enableCaching) }
    .named("appConfig")
    .requireValue(0, "\${app.cache.enabled:false}")  // Set enableCaching from property or default to false

// Create and use the context with automatic resource management
Context.instantiate(
    listOf(repositoryComponent, serviceComponent, configComponent),
    resolver = EnvResolver
).use { context ->
    // Get and use components
    val userService = context.get<UserService>()

    // Get a component with null safety
    val configService = context.getOrNull<ConfigService>()
    if (configService != null) {
        println("Config loaded: ${configService.isEnabled}")
    }

    // Get all implementations of an interface
    val allRepositories = context.getAll<Repository>()
    println("Available repositories: ${allRepositories.keys.joinToString()}")

    // Use the primary service
    val user = userService.getUser("1")
    println("Found user: ${user?.name}")

    // Context will be automatically closed when exiting this block
}
```

## JVM Reflection API

On the JVM, you can use annotations for a Spring-like experience with component scanning:

```kotlin
import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.Context
import de.sfxr.mindi.Plan

// Define components using annotations
@Component
class UserRepository {
    fun findById(id: String): User? = // ...
}

@Component
@Primary  // Mark as the primary implementation of UserService
class UserServiceImpl(
    private val repository: UserRepository,

    @Value("\${app.cache.enabled:false}")
    private val enableCache: Boolean
) : UserService {

    @PostConstruct
    fun initialize() {
        println("UserService initialized with caching: $enableCache")
    }

    override fun getUser(id: String): User? = repository.findById(id)

    @EventListener
    fun onUserEvent(event: UserCreatedEvent) {
        println("User created: ${event.userId}")
    }

    @PreDestroy
    fun cleanup() {
        println("UserService shutting down")
    }
}

// A component that implements AutoCloseable for automatic resource management
@Component
class DatabaseConnection(
    @Value("\${db.url}")
    private val url: String
) : AutoCloseable {

    private var connection: Any? = null

    @PostConstruct
    fun connect() {
        println("Connecting to database at $url")
        connection = "MockConnection" // In real code, this would be a real connection
    }

    fun executeQuery(sql: String): List<String> {
        // In real code, this would execute the query
        return listOf("result1", "result2")
    }

    // This will be automatically called when the context is closed
    override fun close() {
        println("Closing database connection")
        connection = null
    }
}

// Data classes
data class User(val id: String, val name: String)
data class UserCreatedEvent(val userId: String)

// Define beans programmatically with @Bean annotation
object AppConfig {
    @Bean
    fun dataSource(): DataSource {
        return BasicDataSource().apply {
            url = "jdbc:h2:mem:test"
            username = "sa"
        }
    }

    @Bean
    fun userRepository(dataSource: DataSource): UserRepository {
        return JdbcUserRepository(dataSource)
    }

    @Bean("auditService")
    @Qualifier("production")
    fun createAuditService(): AuditService {
        return ProductionAuditService()
    }
}

// Application setup
fun main() {
    // Scan for components in package
    val components = ComponentScanner.findComponents(listOf("com.example.app"))

    // Add beans from configuration object
    val beanComponents = Reflector.reflectFactory(AppConfig)

    // Combine all components
    val allComponents = components + beanComponents

    // Create and use the context with automatic resource management
    Context.instantiate(allComponents).use { context ->
        // Get and use components
        val userService = context.get<UserService>()
        val user = userService.getUser("1")

        // Use a component that implements AutoCloseable
        val db = context.get<DatabaseConnection>()
        val results = db.executeQuery("SELECT * FROM users")

        // Publish an event
        context.publishEvent(UserCreatedEvent("2"))

        // When this block exits:
        // 1. Context.close() is called automatically by .use()
        // 2. @PreDestroy methods are called on all components
        // 3. close() is called on all AutoCloseable components
    }
}
```

### Automatic Resource Management

mindi automatically manages the lifecycle of components that implement `AutoCloseable`. When the context is closed:

1. All components in the context are destroyed in reverse order of creation
2. Components with `@PreDestroy` methods have those methods called
3. Components that implement `AutoCloseable` have their `close()` method called automatically

This makes mindi ideal for managing resources like database connections, file handles, and network connections without leaks, similar to modern Spring applications.

## Key Feature: Static Dependency Resolution

One of mindi's defining features is its static dependency resolution system. Unlike many DI containers that resolve dependencies dynamically during initialization (potentially causing partial startup failures), mindi resolves all dependencies ahead of time:

```kotlin
// Build a plan to verify all dependencies can be resolved
val plan = Plan.build(listOf(component1, component2, component3))

// At this point, mindi has:
// 1. Detected and verified all dependencies
// 2. Identified circular dependencies (and thrown an error if any exist)
// 3. Created a deterministic initialization order
// 4. Validated that all required components can be satisfied

// Only after validation succeeds do we instantiate the context
Context.instantiate(plan).use { context ->
    // Use the context safely with automatic resource management
}
```

This approach offers several significant advantages:

1. **Fail-Fast Behavior**: Detect configuration issues early, before any components are instantiated
2. **No Partial Initialization**: Never end up with a partially initialized application
3. **Deterministic Startup**: Components are always initialized in a consistent order
4. **Better Testing**: Validate component graphs without actually creating instances
5. **Improved Performance**: Resolution happens once, not repeatedly during initialization

For convenience, the instantiation is often combined into a single step:

```kotlin
// Combines plan building and context instantiation in one step
Context.instantiate(listOf(component1, component2, component3)).use { context ->
    // Use the context safely with automatic resource management
}
```

But under the hood, full validation still occurs before any constructors are called.

## Advanced Features

### Hierarchical Contexts

```kotlin
// Create parent context
val parentContext = Context.instantiate(listOf(parentComponent1, parentComponent2))

// Create child context that can access parent components
val childContext = Context.instantiate(
    listOf(childComponent1, childComponent2),
    parentContext
)

// Components in childContext can autowire dependencies from parentContext
```

### Qualified Dependencies

```kotlin
// Define multiple implementations with qualifiers
val mysqlRepositoryComponent = Component { -> MySqlRepository() }
    .withSuperType<_, Repository>()
    .named("mysql")  // Qualifier name

val postgresRepositoryComponent = Component { -> PostgresRepository() }
    .withSuperType<_, Repository>()
    .named("postgres")  // Qualifier name
    .with(primary = true)  // Mark as primary

// Inject a specific implementation using requireQualified
val serviceComponent = Component { repo: Repository ->
    DataService(repo)
}
    .requireQualified(0, "mysql")  // Request the mysql implementation
```

### Function Type Injection

```kotlin
// Define function type components
val transformerComponent = Component { ->
    // Returns a function that transforms String to Int
    { input: String -> input.length }
}
    .named("stringLengthTransformer")

// Inject the function
val processorComponent = Component { transformer: (String) -> Int ->
    DataProcessor { input ->
        val transformed = transformer(input)
        // Process the transformed data
        transformed * 2
    }
}
```

## Notes for Spring Developers

If you're familiar with Spring Framework, mindi provides many similar features:

- `@Component` = Spring's `@Component`
- `@Autowired` = Spring's `@Autowired`
- `@Qualifier` = Spring's `@Qualifier`
- `@Primary` = Spring's `@Primary`
- `@Value` = Spring's `@Value`
- `@PostConstruct` = Spring's `@PostConstruct`
- `@PreDestroy` = Spring's `@PreDestroy`
- `@EventListener` = Spring's `@EventListener`
- `@Bean` = Spring's `@Bean`
- `ComponentScanner` = Spring's component scanning

Key differences:
- mindi is much more lightweight with a smaller API surface
- Full support for Kotlin Multiplatform (JVM, JS, Native)
- Improved type safety through Kotlin's type system
- Explicit functional API in addition to annotations

## License

This project is licensed under the MIT License - see the LICENSE file for details.
