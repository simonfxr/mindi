package de.sfxr.mindi.annotations

/**
 * Marks a method to be called before a component is destroyed.
 *
 * The pre-destroy method will be called when the Context is being closed,
 * allowing components to perform cleanup operations like closing resources
 * or shutting down services.
 *
 * The annotated method must:
 * 1. Take no parameters (other than the implicit 'this')
 * 2. Have public visibility
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class DatabaseService {
 *     private lateinit var connection: Connection
 *
 *     @PostConstruct
 *     fun initialize() {
 *         connection = createConnection()
 *     }
 *
 *     @PreDestroy
 *     fun cleanup() {
 *         connection.close()
 *     }
 * }
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class PreDestroy
