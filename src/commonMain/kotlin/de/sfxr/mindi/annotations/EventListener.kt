package de.sfxr.mindi.annotations

/**
 * Marks a method as an event listener to receive events published to the context.
 *
 * Event listener methods must:
 * 1. Have exactly one parameter (besides the implicit 'this')
 * 2. The parameter type determines what events the listener will receive
 * 3. The listener will receive events of the exact type or subtypes
 *
 * Events are published using the Context.publishEvent method.
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class MyEventHandler {
 *     @EventListener
 *     fun handleUserEvent(event: UserEvent) {
 *         // Handle the event
 *     }
 * }
 *
 * // Publishing an event
 * context.publishEvent(UserEvent("login"))
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventListener
