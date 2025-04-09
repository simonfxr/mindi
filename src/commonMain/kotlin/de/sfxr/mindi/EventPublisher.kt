package de.sfxr.mindi

import kotlin.jvm.JvmInline

/**
 * A type-safe wrapper for publishing events of a specific type.
 *
 * EventPublisher provides a mechanism to ensure events are only published
 * after all relevant listeners are initialized and ready to receive them.
 * This is particularly useful during application startup when components
 * might need to publish events before the entire context is fully initialized.
 *
 * When a component depends on an EventPublisher<T>, the dependency injection system
 * ensures that all listeners for events of type T are constructed and initialized
 * before the component that needs to publish those events. This is achieved by
 * creating an implicit component dependency graph where the EventPublisher<T>
 * component depends on all listener components for type T.
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class UserService(val publisher: EventPublisher<UserEvent>) {
 *     fun createUser(user: User) {
 *         // Create user...
 *         publisher.publishEvent(UserEvent(user, UserEventType.CREATED))
 *     }
 * }
 * ```
 *
 * @param T The specific event type this publisher handles. Must be Any.
 * @property context The context to publish events to. This is injected by mindi.
 */
@JvmInline
value class EventPublisher<T : Any> @PublishedApi internal constructor(
    private val context: Context,
) {
    @PublishedApi
    internal fun publishEvent(event: T, type: TypeProxy<T>) {
        context.publishEvent(event, type)
    }
}

/**
 * Publishes an event of type T to the context.
 * All listeners that handle this event type will receive the event.
 *
 * The underlying Context's publishEvent method ensures listeners are invoked.
 * The mindi planning phase guarantees that this EventPublisher instance is only
 * available *after* all relevant listeners for type T have been fully initialized.
 *
 * @param event The event to publish. Must be non-null and of type T.
 */
inline fun <reified T: Any> EventPublisher<T>.publishEvent(event: T) =
    publishEvent(event, TypeProxy<T>())
