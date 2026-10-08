# mindi - Minimal Dependency Injection for Kotlin

[![Maven Central](https://img.shields.io/maven-central/v/de.sfxr/mindi)](https://central.sonatype.com/artifact/de.sfxr/mindi)
[![Build](https://github.com/simonfxr/mindi/actions/workflows/build.yml/badge.svg?branch=main&event=push)](https://github.com/simonfxr/mindi/actions/workflows/build.yml)
[![Tests](https://github.com/simonfxr/mindi/actions/workflows/test.yml/badge.svg?branch=main&event=push)](https://github.com/simonfxr/mindi/actions/workflows/test.yml)
[![Publish](https://github.com/simonfxr/mindi/actions/workflows/publish.yml/badge.svg)](https://github.com/simonfxr/mindi/actions/workflows/publish.yml)

[API documentation](https://simonfxr.github.io/mindi/)

mindi is a lightweight, flexible dependency injection framework for Kotlin Multiplatform projects. It provides a powerful DI container with Spring-like features while maintaining a small footprint and Kotlin-first design.

JVM (Java 11+) is the tier-1 platform, supporting both the functional API and annotation-based reflection. JS and the configured Native targets support the functional API.

The framework's standout feature is its static dependency resolution system, which validates declared component dependencies before instantiation. Missing or ambiguous dependencies and dependency cycles fail before managed constructors run. Constructors and lifecycle callbacks can still fail at runtime.

## Features

- **Static Dependency Resolution**: Declared component dependencies are validated before managed constructors are called
- **Fail-Fast Validation**: Build and validate component graphs at application startup, catching configuration errors early
- **Multiplatform Support**: Targets JVM 11+, JS, Linux x64/ARM64, and Windows x64
- **Annotation-based Component Scanning**: Automatic discovery of components using annotations (JVM)
- **@Bean Factory Methods**: Define beans programmatically in configuration objects
- **Functional API**: Define components using Kotlin's type-safe DSL
- **Hierarchical Contexts**: Parent-child relationships for modular applications
- **Value Resolution**: Environment variable and property substitution
- **Lifecycle Management**: PostConstruct/PreDestroy hooks and automatic resource cleanup
- **AutoCloseable Support**: JVM-reflected components whose declared type implements AutoCloseable are automatically closed; functional components use `.onClose { close() }`
- **Event System**: Publish-subscribe pattern with type-safe event handlers
- **Type-safe Dependency Resolution**: Autowire by type with generics support
- **Qualifier Support**: Disambiguate multiple implementations of the same interface
- **Primary Components**: Designate default implementations
- **Optional Dependencies**: Graceful handling of missing dependencies

## Getting Started

Maintainers: see the [local Maven Central publishing guide](docs/publishing.md).

Add mindi to your project:

```kotlin
// build.gradle.kts
dependencies {
    implementation("de.sfxr:mindi:0.3.0")
}
```

## Common API Example

The functional API allows you to define components and their dependencies explicitly:

```kotlin
import de.sfxr.mindi.*

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
    .requireValue(0, "\${app.cache.enabled:false}")  // Resolve from environment or default to false

// Create and use the context with automatic resource management
Context.instantiate(
    listOf(repositoryComponent, serviceComponent, configComponent),
    resolver = EnvResolver
).use { context ->
    // Get and use components
    val userService = context.get<UserService>()

    // Get a component with null safety
    val config = context.getOrNull<AppConfig>()
    if (config != null) {
        println("Caching enabled: ${config.enableCaching}")
    }

    // Get all implementations of an interface
    val allRepositories = context.getAll<UserRepository>()
    println("Available repositories: ${allRepositories.keys.joinToString()}")

    // Use the primary service
    val user = userService.getUser("1")
    println("Found user: ${user?.name}")

    // Context will be automatically closed when exiting this block
}
```

## JVM Reflection API

On the JVM, you can use annotations for a Spring-like experience with component scanning. This illustrative application uses application-specific service and data-source types:

```kotlin
import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflectFactory
import de.sfxr.mindi.Context

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
    val beanComponents = Reflector.Default.reflectFactory(AppConfig)

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
        // 2. Components are closed in reverse creation order, running their
        //    @PreDestroy callbacks followed by AutoCloseable.close()
    }
}
```

### Component scanning limits

The handwritten scanner recursively searches package resources exposed by the supplied class loader (the thread context class loader by default). It supports filesystem directories, standard JARs, and one level of nested JARs. The loader must expose the package resources and be able to load the discovered classes; JARs without package directory entries are not generally discoverable via `URLClassLoader`, and arbitrary executable/fat-JAR layouts are not automatically supported.

Scanning first searches class bytes for configured annotation descriptors, then loads candidates with `Class.forName(name, false, classLoader)` and checks their annotations. This is a byte-substring heuristic, not a classfile parser: unannotated classes containing the descriptor can also be loaded. Class loading requests no initialization, but subsequent Kotlin reflection may initialize objects. This optimization is not lazy component instantiation; `Context.instantiate` eagerly creates the components included in its plan.

### Automatic Resource Management

mindi registers automatic cleanup for JVM-reflected components whose declared type implements `AutoCloseable` (for `@Bean`, this is the method's declared return type). For functional components, register cleanup explicitly:

```kotlin
val connectionComponent = Component { -> DatabaseConnection("jdbc:h2:mem:test") }
    .onClose { close() }
```

When the context is closed:

1. All components in the context are destroyed in reverse order of creation
2. Each component's registered cleanup callback runs; for reflected components, `@PreDestroy` callbacks precede automatic `close()`
3. If a component's cleanup throws an exception, remaining components are still processed; multiple exceptions are collected as suppressed exceptions

Cleanup callbacks should release resources reliably even when startup fails. If one cleanup callback throws, remaining cleanup callbacks are still attempted, with later failures added as suppressed exceptions.

Reflected `@PreDestroy` callbacks run from subclass to superclass. Automatic `AutoCloseable.close()` runs last, exactly once even if that method also has `@PreDestroy`. Ordering between callbacks declared in the same class is unspecified.

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
2. **Earlier Graph Errors**: Dependency graph errors do not leave partially created managed components
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

Under the hood, graph validation still occurs before managed constructors are called. External values are resolved and parsed by `Context.instantiate`, not `Plan.build`, before component creation. This does not validate arbitrary work inside constructors, factories, or lifecycle callbacks. If startup throws an exception, the context attempts to clean up components already constructed; it cannot undo external side effects or clean up an object whose constructor did not return.

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

Reflected injection points accept at most one distinct qualifier (including custom meta-annotations). Multiple qualifiers are rejected during reflection rather than choosing one arbitrarily. Components may expose multiple qualifiers.

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
- Functional API on JVM, JS, and the configured Linux/Windows Native targets; annotation reflection and scanning are JVM-only
- Improved type safety through Kotlin's type system
- Explicit functional API in addition to annotations

## License

This project is licensed under the MIT License - see the LICENSE file for details.
