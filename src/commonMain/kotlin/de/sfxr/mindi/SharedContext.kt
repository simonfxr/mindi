package de.sfxr.mindi

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Shared data and metadata between contexts in a hierarchy.
 *
 * This class maintains component definitions and event listener mappings that are
 * shared across a context hierarchy. It's responsible for resolving event types to
 * their listeners and propagating events throughout the context tree.
 *
 * @property parent Parent shared context (for hierarchical event propagation)
 * @property components List of component definitions
 */
@OptIn(ExperimentalContracts::class)
class SharedContext(
    internal val parent: SharedContext?,
    internal val components: List<Component<*>>,
) {

    /**
     * Index into the component/listener tables for event routing.
     *
     * @property depth Depth in the context hierarchy (0 = current context, 1 = parent, etc)
     * @property index Index in the components list at the given depth
     * @property listener Index in the component's listenerHandlers list
     */
    internal data class ListenerIndex(val depth: Int, val index: Int, val listener: Int): Comparable<ListenerIndex> {
        override fun compareTo(other: ListenerIndex): Int {
            var r = depth.compareTo(other.depth)
            if (r != 0) return r
            r = index.compareTo(other.index)
            if (r != 0) return r
            r = listener.compareTo(other.listener)
            return r
        }
    }

    /**
     * Flattened list of component lists from this context and all parent contexts
     */
    internal val componentsTable = generateSequence<SharedContext>(this) { it.parent }.map { it.components }.toList()

    /**
     * Map from concrete event types to listeners that handle exactly that type
     */
    private val listenersByConcreteType: Map<KType, List<ListenerIndex>> = buildMap<KType, ArrayList<ListenerIndex>> {
        for ((d, components) in componentsTable.withIndex())
            for ((i, c) in components.withIndex())
                for ((j, l) in c.listenerArgs.withIndex())
                    getOrPut(l) { ArrayList(1) }.add(ListenerIndex(d, i, j))
        for (e in values)
            e.trimToSize()
    }

    /**
     * Cache for polymorphic event type resolution
     */
    private var listenersByType = newConcurrentMap<KType, List<ListenerIndex>>()

    /**
     * Resolves all listeners that can handle the given event type.
     *
     * This includes listeners that listen for the exact type as well as
     * listeners that listen for any supertype of the event.
     *
     * @param eventType The event class to resolve listeners for
     * @return List of listener indices that can handle this event type
     */
    private fun resolveListeners(event: Any, eventType: KType): List<ListenerIndex> {
        return listenersByType[eventType] ?: run {
            buildList {
                for ((listenerType, cs) in listenersByConcreteType) {
                    val listenerClass = listenerType.classifier as? KClass<*>
                    if (listenerClass != null && listenerClass.isInstance(event)) {
                        addAll(cs)
                    }
                }
                sort()
            }.also {
                listenersByType[eventType] = it
            }
        }
    }

    /**
     * Publishes an event to all components with matching event listeners.
     *
     * @param context The context from which the event is published
     * @param event The event object to publish
     * @param handle Callback for handling exceptions during event dispatch
     */
    internal inline fun publishEvent(context: Context, event: Any, type: KType, handle: (Int, Component<*>, Any, Exception) -> Unit) {
        contract {
            callsInPlace(handle)
        }
        check(context.shared === this)
        for ((d, i, l) in resolveListeners(event, type)) {
            val component = componentsTable[d][i]
            val handler = component.listenerHandlers[l]
            val receiver = context.receiverAt(d, i)
            try {
                handler(receiver, event)
            } catch (e: Exception) {
                handle(d, component, receiver, e)
            }
        }
    }

    /**
     * Looks up a component instance in the context hierarchy.
     *
     * @param d Depth in the context hierarchy (0 = current context, 1 = parent, etc)
     * @param i Index in the components list at the given depth
     * @return Component instance at the given position
     */
    private fun Context.receiverAt(d: Int, i: Int): Any =
        if (d == 0) instances[i]
        else parents[d - 1].instances[i]
}