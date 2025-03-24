package de.sfxr.mindi.annotations

/**
 * Marks a class as a component that can be managed by the dependency injection system.
 *
 * Components are automatically discovered, instantiated, and wired together
 * based on their dependencies. Components can be identified by name using the
 * value parameter, which is useful for disambiguating between multiple
 * implementations of the same interface.
 *
 * Components become part of the dependency injection container when:
 * 1. They are scanned during container initialization
 * 2. They are directly referenced by other components
 *
 * All components are singletons by default - only one instance of each component
 * is created per Context.
 *
 * Example usage:
 * ```kotlin
 * // Basic component with default name
 * @Component
 * class SimpleService { ... }
 *
 * // Named component for disambiguation
 * @Component("customName")
 * class NamedService { ... }
 *
 * // Component implementing an interface
 * @Component
 * class DefaultRepository : Repository { ... }
 * ```
 *
 * @property value Optional name for the component (defaults to lowercase class name)
 *            This name can be used with @Qualifier to select a specific implementation
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Component(val value: String = "")
