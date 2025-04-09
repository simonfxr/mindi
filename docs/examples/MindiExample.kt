package de.sfxr.mindi.examples

import de.sfxr.mindi.*
import kotlin.reflect.typeOf

/**
 * A simple example demonstrating the core features of mindi
 */
fun mindiExample() {
    // 1. Define your services and components
    interface MessageRepository {
        fun getMessage(id: String): String
    }

    interface MessageService {
        fun getFormattedMessage(id: String): String
    }

    class SimpleMessageRepository : MessageRepository {
        override fun getMessage(id: String): String = "Hello, $id!"
    }

    class DefaultMessageService(
        private val repository: MessageRepository,
        private val prefix: String = "[MSG]"
    ) : MessageService {
        override fun getFormattedMessage(id: String): String =
            "$prefix ${repository.getMessage(id)}"
    }

    class MessageEventListener {
        var eventCount = 0

        fun onMessageRequested(event: MessageRequestedEvent) {
            eventCount++
            println("Message requested: ${event.id} (Total: $eventCount)")
        }
    }

    data class MessageRequestedEvent(val id: String)

    // 2. Create component definitions using the functional API
    val repositoryComponent = Component { -> SimpleMessageRepository() }
        .withSuperType<_, MessageRepository>()
        .named("messageRepository")

    val serviceComponent = Component { repo: MessageRepository ->
        DefaultMessageService(repo)
    }
        .withSuperType<_, MessageService>()
        .named("messageService")

    val listenerComponent = Component { -> MessageEventListener() }
        .listening<MessageRequestedEvent> { onMessageRequested(it) }
        .named("eventListener")

    // 3. Create and use the context with automatic resource management
    Context.instantiate(listOf(
        repositoryComponent,
        serviceComponent,
        listenerComponent
    )).use { context ->
        // Get the service by type
        val messageService = context.get<MessageService>()

        // Get a message and publish an event
        val userId = "user123"
        val message = messageService.getFormattedMessage(userId)
        println("Result: $message")

        // Publish an event
        context.publishEvent(MessageRequestedEvent(userId))
        context.publishEvent(MessageRequestedEvent("another-user"))

        // Context will be automatically closed when exiting this block
    }
}