package de.sfxr.mindi

import de.sfxr.mindi.internal.associateUnique
import kotlin.concurrent.Volatile
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * The runtime container that manages component instances and their lifecycle.
 *
 * Context is the central component of the Mindi dependency injection framework. It's responsible for:
 * 1. Holding component instances and managing their lifecycle
 * 2. Supporting hierarchical dependency injection through parent contexts
 * 3. Publishing events to components with matching @EventListener methods
 * 4. Proper resource cleanup during shutdown
 *
 * A Context is created using a Plan:
 * ```kotlin
 * // Create a plan from component definitions
 * val plan = Plan.build(listOf(component1, component2))
 *
 * // Instantiate the context with all components
 * val context = Context.instantiate(plan)
 *
 * // Use the context
 * context.publishEvent(StartupEvent())
 *
 * // Close the context when done to clean up resources
 * context.close()
 * ```
 *
 * Context implements AutoCloseable, so it can be used with try-with-resources:
 * ```kotlin
 * Context.instantiate(plan).use { context ->
 *     // Use the context
 *     context.publishEvent(StartupEvent())
 * } // Context is automatically closed when leaving this block
 * ```
 *
 * @property parents List of parent contexts (for hierarchical DI)
 * @property shared Shared data and metadata between contexts
 * @property instances The managed component instances
 */
class Context(
    internal val parents: List<Context>,
    internal val shared: SharedContext,
    internal val instances: MutableList<Any>,
): AutoCloseable {

    /**
     * Interface for handling exceptions that occur during event listener invocation.
     *
     * Implement this interface and inject it into the Context to provide custom
     * exception handling for event listeners. By default, exceptions are printed
     * to the console.
     */
    interface ListenerExceptionHandler {
        /**
         * Context information for an exception that occurred during event listener invocation.
         *
         * @property context The Context in which the exception occurred
         * @property event The event that was being processed
         * @property eventType The KType of the event
         * @property listener The component instance that was listening for the event
         * @property component The Component definition of the listener
         */
        data class ExceptionContext(val context: Context, val event: Any, val eventType: KType, val listener: Any, val component: Component<*>)

        /**
         * Handles an exception that occurred during event listener invocation.
         *
         * @param context Contextual information about the exception
         * @param exception The exception that was thrown
         */
        fun handleListenerException(context: ExceptionContext, exception: Exception)
    }

    /** Direct parent context, or null if this is a root context */
    val parent: Context? get() = parents.getOrNull(0)

    @Volatile
    var isClosed = false
        private set

    @Volatile
    var isStarted = false
        private set

    @Volatile
    private var listenerExceptionHandler: ListenerExceptionHandler? = null

    /**
     * Gets a component of the specified type from the context.
     *
     * This method resolves the component by type, giving preference to:
     * 1. Primary components in the current context
     * 2. Non-primary components in the current context
     * 3. Components from parent contexts (in the same order)
     *
     * If multiple eligible components are found at the same precedence level,
     * an exception is thrown (unless a specific component is requested with a qualifier).
     *
     * @param qualifier Optional qualifier to select a specific component when multiple are available
     * @return The resolved component instance
     * @throws IllegalStateException If no component is found or if multiple candidates are ambiguous
     */
    inline fun <reified T : Any> get(qualifier: String? = null): T = get(TypeProxy<T>(), qualifier)

    /**
     * Gets a component of the specified type from the context.
     *
     * This method provides the same behavior as the reified version but allows
     * for dynamic type resolution when the type isn't known at compile time.
     *
     * @param type The type of component to retrieve
     * @param qualifier Optional qualifier to select a specific component when multiple are available
     * @return The resolved component instance
     * @throws IllegalStateException If no component is found or if multiple candidates are ambiguous
     */
    fun <T : Any> get(type: TypeProxy<T>, qualifier: String? = null): T {
        return getOrNull<T>(type, qualifier)
            ?: throw IllegalStateException("No component found of type $type${qualifier?.let { " with qualifier '$it'" } ?: ""}")
    }

    /**
     * Gets a component of the specified type from the context, or returns null if not found.
     *
     * This method is similar to get<T>() but returns null instead of throwing an exception
     * when the component can't be found.
     *
     * @param qualifier Optional qualifier to select a specific component
     * @return The resolved component instance, or null if not found
     */
    inline fun <reified T : Any> getOrNull(qualifier: String? = null): T? = getOrNull(TypeProxy<T>(), qualifier)

    /**
     * Gets a component of the specified type from the context, or returns null if not found.
     *
     * This method is similar to get<T>() but returns null instead of throwing an exception
     * when the component can't be found.
     *
     * @param type The type of component to retrieve
     * @param qualifier Optional qualifier to select a specific component
     * @return The resolved component instance, or null if not found
     */
    fun <T : Any> getOrNull(type: TypeProxy<T>, qualifier: String? = null): T? {
        val dependency = Dependency(type.type, qualifier) as Dependency.Single
        val location = findComponentIndices(dependency).firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return if (location.depth == 0) {
            instances[location.index]
        } else {
            parents[location.depth - 1].instances[location.index]
        } as T
    }

    /**
     * Gets all components of the specified type from the context.
     *
     * This method returns a map of component names to instances for all components
     * that match the specified type, including components from parent contexts.
     * The map will be empty if no matching components are found.
     *
     * @return Map of component names to instances
     */
    inline fun <reified T : Any> getAll(): Map<String, T> = getAll(TypeProxy<T>())

    /**
     * Gets all components of the specified type from the context.
     *
     * This method returns a map of component names to instances for all components
     * that match the specified type, including components from parent contexts.
     * The map will be empty if no matching components are found.
     *
     * @param type The type of components to retrieve
     * @return Map of component names to instances
     */
    fun <T : Any> getAll(type: TypeProxy<T>): Map<String, T> {
        val indices = findByType(type.type).takeIf { it.isNotEmpty() } ?: return emptyMap()
        return buildMap {
            for (index in indices) {
                val component = shared.componentsTable[index.depth][index.index]
                @Suppress("UNCHECKED_CAST")
                this[component.name] = if (index.depth == 0)
                    instances[index.index] as T
                else
                    parents[index.depth - 1].instances[index.index] as T
            }
        }
    }

    /**
     * Finds the indices of all components that match the given dependency.
     *
     * This is an internal method that replicates the logic from Plan.Planning.resolveSingleProvider
     * but operates on instances rather than component definitions.
     *
     * @param dependency The dependency to resolve
     * @return List of indices pointing to matching components
     */
    private fun findComponentIndices(dependency: Dependency.Single): List<Instantiation.Index> =
        Plan.resolveSingleProvider(null, findByType(dependency.type), dependency.copy(required=false)) {
            shared.componentsTable[it.depth][it.index]
        }

    /**
     * Finds all components that match the given type.
     */
    private fun findByType(type: KType): List<Instantiation.Index> = buildList {
        for ((d, components) in shared.componentsTable.withIndex())
            for ((i, component) in components.withIndex())
                if (component.isSubtypeOf(type))
                    add(Instantiation.Index(d, i))
    }

    /**
     * Publishes an event to all components with matching event listeners.
     *
     * Events are dispatched to components with @EventListener methods
     * that accept the event type or its supertype.
     *
     * @param event The event object to publish
     */
    inline fun <reified T: Any> publishEvent(event: T): Unit =
        publishEvent(event, typeOf<T>())

    @PublishedApi
    internal fun publishEvent(event: Any, type: KType) {
        shared.publishEvent(this, event, type) { d, c, l, e ->
            if (d == 0)
                handleListenerException(event, type, c, l, e)
            else
                parents[d - 1].handleListenerException(event, type, c, l, e)
        }
    }

    private fun handleListenerException(event: Any, etype: KType, c: Component<*>, l: Any, e: Exception) {
        val h = listenerExceptionHandler ?: run {
            println("*** Exception while publishing event ${event::class} to component ${c.klass} aka ${c.names}: ${e.message}(${e::class})")
            return
        }
        h.handleListenerException(ListenerExceptionHandler.ExceptionContext(this, event, etype, l, c), e)
    }

    /**
     * Closes the context and all component instances it manages.
     *
     * Invokes all component @PreDestroy methods and performs resource cleanup.
     * If multiple components throw exceptions during close, they're collected as suppressed exceptions.
     */
    override fun close() {
        if (isClosed)
            return
        isClosed = true

        var firstException: Exception? = null
        while (!instances.isEmpty()) {
            val v = instances.removeLast()
            try {
                (shared.components[instances.size].close ?: continue)(v)
            } catch (e: Exception) {
                firstException?.addSuppressed(e)
                firstException = firstException ?: e
            }
        }
        if (firstException != null) {
            throw firstException
        }
    }

    companion object {
        /**
         * Special built-in component that provides the Context itself as an injectable dependency
         */
        internal val Component =
            Component<Context>(
                type = typeOf<Context>(),
                defaultName = "context",
                primary = true,
                construct = { this },
                constructorArgs = emptyList(),
                fields = listOf(Dependency.Single(typeOf<ListenerExceptionHandler>(), null, required=false)),
                setters = listOf { c, x -> x?.let { (c as Context).listenerExceptionHandler = it as ListenerExceptionHandler } },
            )

        /**
         * Creates a new Context directly from a list of components.
         *
         * This is a convenience method that creates a Plan and then instantiates a Context,
         * combining the two most common steps in one operation.
         *
         * @param components The components to include in the context
         * @param parentContext Optional parent context for hierarchical DI
         * @param resolver The resolver for external values (defaults to environment variables)
         * @return A populated Context with all components instantiated
         */
        fun instantiate(
            components: List<Component<*>>,
            parentContext: Context? = null,
            resolver: ValueResolver = EnvResolver,
        ): Context {
            val plan = Plan.build(components, parentContext?.shared)
            return instantiate(plan, parentContext, resolver)
        }

        /**
         * Executes the plan to create and populate a Context with component instances.
         *
         * This method follows these steps:
         * 1. Parse all value dependencies using the provided resolver
         * 2. Create a new Context
         * 3. Instantiate all components in dependency order
         * 4. Inject field dependencies
         * 5. Call @PostConstruct methods
         * 6. Return the fully initialized Context
         *
         * @param plan The plan to instantiate
         * @param parentContext The parent context of the context to instantiate
         * @param resolver The resolver for external values (defaults to environment variables)
         * @return A populated Context with all components instantiated
         */
        fun instantiate(
            plan: Plan,
            parentContext: Context? = null,
            resolver: ValueResolver = EnvResolver,
        ): Context {
            check(plan.parents.firstOrNull() === parentContext?.shared) {
                "plan hierarchy must match context hierarchy"
            }

            check(parentContext?.isClosed != true) { "parent context already closed" }
            check(parentContext?.isStarted != false) { "parent context not yet fully started" }

            val argValues = plan.prefill(resolver) { it.constructorArgs }
            val fieldValues = plan.prefill(resolver) { it.fields }

            val parentContexts = generateSequence(parentContext) { it.parent }.toList()

            val instances = ArrayList<Any>(plan.components.size)

            fun instance(i: Instantiation.Index): Any =
                if (i.depth > 0) parentContexts[i.depth - 1].instances[i.index]
                else instances[i.index]

            val context = Context(parentContexts, plan.shared, instances)

            try {
                for ((slotOrUnset, argIndices) in plan.instantiations) {
                    if (context.isClosed)
                        throw IllegalStateException("context closed during construction")

                    val slot: Int
                    val c: Component<*>
                    val deps: List<Dependency>
                    val values: List<Any>
                    if (slotOrUnset == -1) {
                        slot = instances.size
                        c = plan.components[slot]
                        deps = c.constructorArgs
                        values = argValues[slot]
                    } else {
                        slot = slotOrUnset
                        c = plan.components[slot]
                        deps = c.fields
                        values = fieldValues[slot]
                    }

                    val args = deps.withIndex().map { (j, d) ->
                        when (d) {
                            is Dependency.Value<*> -> values[j]
                            is Dependency.Single -> if (argIndices[j].isEmpty()) null else instance(argIndices[j].first())
                            is Dependency.Multiple -> argIndices[j].associateUnique { plan.component(it).name to instance(it) }
                        }
                    }

                    if (slotOrUnset == -1) {
                        val construct = c.construct
                        instances.add(context.construct(args))
                    } else {
                        val obj = instances[slot]
                        for ((j, v) in args.withIndex())
                            c.setters[j](obj, v)
                        c.postConstruct?.invoke(obj)
                    }
                }

                context.isStarted = true
                return context
            } catch (e: Exception) {
                try {
                    context.close()
                } catch (closeException: Exception) {
                    e.addSuppressed(closeException)
                }
                throw e
            }
        }
    }
}
