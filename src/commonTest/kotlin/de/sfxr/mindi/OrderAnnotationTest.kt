package de.sfxr.mindi

import de.sfxr.mindi.annotations.Order
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the @Order annotation functionality.
 */
class OrderAnnotationTest {

    // Interface that the ordered components will implement
    interface Handler {
        val name: String
    }

    // Component with explicitly high precedence (low number = high precedence)
    class HighPriorityHandler : Handler {
        override val name = "high"
    }

    // Component with medium precedence
    class MediumPriorityHandler : Handler {
        override val name = "medium"
    }

    // Component with no explicit order - should use default
    class DefaultHandler : Handler {
        override val name = "default"
    }

    // Component with explicitly low precedence
    class LowPriorityHandler : Handler {
        override val name = "low"
    }

    // Service that consumes the ordered handlers
    class HandlerRegistry(val handlers: List<Handler>) {
        fun getHandlerNames(): List<String> = handlers.map { it.name }
    }

    @Test
    fun testComponentsOrderedByOrderValue() {
        // Create the handler components manually
        val highComponent = Component { -> HighPriorityHandler() }
            .named("high")
            .copy(order = 1)
            .withSuperType<_, Handler>()

        val mediumComponent = Component { -> MediumPriorityHandler() }
            .named("medium")
            .copy(order = 2)
            .withSuperType<_, Handler>()

        val defaultComponent = Component { -> DefaultHandler() }
            .named("default")
            .copy(order = Component.DEFAULT_ORDER)
            .withSuperType<_, Handler>()

        val lowComponent = Component { -> LowPriorityHandler() }
            .named("low")
            .copy(order = 3)
            .withSuperType<_, Handler>()

        // Create a registry that depends on all handlers
        val registryComponent = Component { handlers: List<Handler> -> HandlerRegistry(handlers) }
            .named("registry")

        // Build plan and create context with deliberately shuffled component order
        val plan = Plan.build(listOf(
            mediumComponent,   // Order 2
            defaultComponent,  // Order DEFAULT
            lowComponent,      // Order 3
            highComponent,     // Order 1
            registryComponent
        ))

        val context = Context.instantiate(plan)

        // Get the registry from the context
        val registry = context.shared.components
            .firstOrNull { it.klass == HandlerRegistry::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as HandlerRegistry
            }

        val handlerNames = registry?.getHandlerNames()

        // Expected order: high, medium, low, default
        // (Components with lower order values come first)
        assertEquals(
            listOf("high", "medium", "low", "default"),
            handlerNames,
            "Handlers should be ordered according to their order values"
        )

        context.close()
    }

    @Test
    fun testComponentsWithSameOrderValue() {
        // Create the handler components with the same order value but different names
        val aComponent = Component { -> object : Handler { override val name = "A" } }
            .named("a")
            .copy(order = 5)
            .withSuperType<_, Handler>()

        val bComponent = Component { -> object : Handler { override val name = "B" } }
            .named("b")
            .copy(order = 5)
            .withSuperType<_, Handler>()

        val cComponent = Component { -> object : Handler { override val name = "C" } }
            .named("c")
            .copy(order = 5)
            .withSuperType<_, Handler>()

        // Create a registry that depends on all handlers
        val registryComponent = Component { handlers: List<Handler> -> HandlerRegistry(handlers) }
            .named("registry")

        // Build plan and create context with deliberately shuffled component order
        val plan = Plan.build(listOf(
            cComponent,      // Order 5, name "C"
            aComponent,      // Order 5, name "A"
            bComponent,      // Order 5, name "B"
            registryComponent
        ))

        val context = Context.instantiate(plan)

        // Get the registry from the context
        val registry = context.shared.components
            .firstOrNull { it.klass == HandlerRegistry::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as HandlerRegistry
            }

        val handlerNames = registry?.getHandlerNames()

        // Components with same order value should be sorted alphabetically by name
        assertEquals(
            listOf("C", "A", "B"),
            handlerNames,
            "Handlers with the same order value should be sorted by definition order"
        )

        context.close()
    }
}