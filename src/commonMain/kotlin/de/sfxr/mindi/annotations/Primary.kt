package de.sfxr.mindi.annotations

/**
 * Marks a component as the primary implementation of its type.
 *
 * When multiple components of the same type exist, this annotation helps
 * the dependency injection system resolve ambiguity. If only one component
 * has the @Primary annotation, it will be chosen over other non-primary
 * components of the same type.
 *
 * Example usage:
 * ```kotlin
 * interface PaymentService { ... }
 *
 * @Component
 * class PayPalService : PaymentService { ... }
 *
 * @Primary
 * @Component
 * class StripeService : PaymentService { ... }
 * ```
 *
 * In this example, StripeService will be injected when a PaymentService is required,
 * unless explicitly qualified with a different name.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Primary