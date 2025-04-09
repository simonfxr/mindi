package de.sfxr.mindi.annotations

/**
 * Specifies the ordering of a component when multiple components are injected as a collection.
 *
 * Components with lower order values have higher precedence and appear earlier in collections.
 * Components with the same order value are sorted by their names.
 *
 * This annotation can be applied to:
 * - Component classes to define their order in a collection
 * - Functions in configuration classes annotated with @Bean
 *
 * Example usage:
 * ```kotlin
 * @Order(1)
 * @Component
 * class HighPriorityHandler : MessageHandler { ... }
 *
 * @Component
 * class DefaultHandler : MessageHandler { ... }
 *
 * @Order(3)
 * @Component
 * class LowPriorityHandler : MessageHandler { ... }
 *
 * @Component
 * class HandlerRegistry(
 *     // Will be injected in order: HighPriorityHandler, DefaultHandler, LowPriorityHandler
 *     private val handlers: List<MessageHandler>
 * ) {
 *     fun handleMessage(msg: String) {
 *         for (handler in handlers) {
 *             handler.handle(msg)
 *         }
 *     }
 * }
 * ```
 *
 * Components without an @Order annotation are assigned a default order value of
 * [DEFAULT_ORDER], which means they appear after explicitly ordered components
 * but before components with higher values.
 *
 * @property value The order value for this component (lower values have higher precedence)
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION
)
annotation class Order(val value: Int = DEFAULT_ORDER) {
    companion object {
        /**
         * The default order value for components that don't have an explicit @Order annotation.
         */
        const val DEFAULT_ORDER: Int = Int.MAX_VALUE / 2
    }
}