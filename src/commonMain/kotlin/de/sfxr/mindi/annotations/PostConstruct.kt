package de.sfxr.mindi.annotations

/**
 * Marks a method to be called after a component has been fully initialized.
 *
 * The post-construct method will be called after:
 * 1. The component has been instantiated
 * 2. All dependencies have been injected
 *
 * This is useful for initialization logic that depends on injected components.
 * The annotated method must:
 * 1. Take no parameters (other than the implicit 'this')
 * 2. Have public visibility
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class MyService {
 *     @Autowired
 *     lateinit var dependency: SomeDependency
 *
 *     @PostConstruct
 *     fun initialize() {
 *         // Initialization logic using dependencies
 *         dependency.configure()
 *     }
 * }
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class PostConstruct