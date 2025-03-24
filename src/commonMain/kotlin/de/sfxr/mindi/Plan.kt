package de.sfxr.mindi

import de.sfxr.mindi.internal.sizeOr
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

internal typealias Index = Instantiation.Index

/**
 * An executable plan for instantiating components and their dependencies.
 *
 * The Plan contains all information needed to create a Context with all required components
 * and their dependencies properly wired together. It represents a fully resolved dependency graph
 * that can be executed to create component instances.
 *
 * The Plan handles:
 * - Component creation in the correct dependency order
 * - Constructor, field, and setter injection
 * - Resolution of dependency cycles and ambiguities
 * - Parent-child context relationships
 * - External value resolution via ValueResolver
 *
 * Typical usage:
 * ```kotlin
 * // Create a plan
 * val plan = Plan.build(scanComponents())
 *
 * // Execute the plan to create a context
 * val context = Context.instantiate(plan)
 * ```
 *
 * For custom value resolution:
 * ```kotlin
 * // Create a custom value resolver
 * val customResolver = object : ValueResolver {
 *     override fun resolveValue(key: String): String? = myCustomConfig[key]
 * }
 *
 * // Use the custom resolver when instantiating
 * val context = Context.instantiate(plan, resolver = customResolver)
 * ```
 *
 * @property parents List of parent contexts in hierarchical order
 * @property shared Shared data and metadata for this context
 * @property instantiations Component instantiation specifications in dependency order
 */
class Plan internal constructor(
    internal val parents: List<SharedContext>,
    internal val shared: SharedContext,
    internal val instantiations: List<Instantiation>,
) {
    companion object {
        /**
         * Builds a new Plan from a sequence of component definitions.
         *
         * This method analyzes the component definitions and their dependencies,
         * resolves dependency relationships, and creates an optimized plan for
         * instantiating all components in the correct order.
         *
         * @param componentSequence The components to include in the plan
         * @param parent Optional parent plan for hierarchical dependency injection
         * @return A fully constructed Plan that can be executed by Context.instantiate
         */
        fun build(componentSequence: Iterable<Component<*>>, parent: Plan? = null): Plan =
            Planning(componentSequence, parent?.shared).plan()

        internal fun build(componentSequence: Iterable<Component<*>>, parent: SharedContext?): Plan =
            Planning(componentSequence, parent).plan()

        internal inline fun resolveSingleProvider(all: List<Index>, dep: Dependency.Single, component: (Index) -> Component<*>): List<Index> {
            val candidates = all.filter { dep.qualifier == null || dep.qualifier in component(it).names }
            if (candidates.size == 1)
                return candidates
            else if (candidates.isEmpty() && dep.required)
                throw IllegalStateException("Failed to find provider for ${dep.type}")
            else if (dep.qualifier != null)
                throw IllegalStateException("Failed to find unique provider qualified '${dep.qualifier}' for ${dep.type}")

            // Iterate dependencies level by level
            // Components at level k shadow all components at level > k
            // Find unique primary component at level k or unique non primary at level k
            // if neither is found, go to next level
            var depth = 0
            while (candidates.isNotEmpty() && depth != Int.MAX_VALUE) {
                val currentProviders = candidates.filter { it.depth == depth }
                val providers = currentProviders.filter { i -> component(i).primary }.takeIf { it.isNotEmpty() } ?: currentProviders
                if (providers.size > 1)
                    throw IllegalStateException("Providers for dependency ${dep.type} are ambiguous")
                if (providers.size == 1)
                    return providers
                depth = candidates.minOf { if (it.depth > depth) it.depth else Int.MAX_VALUE }
            }

            if (dep.required)
                throw IllegalStateException("Failed to find provider for ${dep.type}")

            return emptyList()
        }
    }


    /** The component definitions in this plan */
    val components get() = shared.components

    /**
     * Pre-fills value dependencies from a resolver.
     *
     * This internal method is used during context instantiation to resolve
     * all @Value dependencies before component creation begins.
     *
     * @param resolver The ValueResolver to use for resolving value dependencies
     * @param selector Function that selects which dependencies to prefill (constructor or field)
     * @return List of resolved values for each component's dependencies
     */
    internal inline fun prefill(resolver: ValueResolver, selector: (Component<*>) -> List<Dependency>) = components.map { c ->
        selector(c).map { a ->
            when (a) {
                is Dependency.Value<*> -> resolver.resolveValue(a)
                else -> Unit
            }
        }
    }

    /**
     * Retrieves a component definition from the component hierarchy.
     *
     * @param i Index specifying which component to retrieve
     * @return The component definition at the specified position
     */
    internal fun component(i: Instantiation.Index): Component<*> =
        if (i.depth == 0) components[i.index]
        else parents[i.depth - 1].components[i.index]

    private class Planning(
        componentSequence: Iterable<Component<*>>,
        parent: SharedContext?,
    ) {

        /**
         * List of parent contexts in hierarchical order
         */
        private val parents: List<SharedContext> = generateSequence(parent) { it.parent }.toList()

        /**
         * All components in this context, including the built-in context component
         */
        private val components: MutableList<Component<*>> = ArrayList<Component<*>>(1 + componentSequence.sizeOr(0)).apply {
            add(Context.Component)
            addAll(componentSequence)
        }

        /**
         * Map from concrete types to components that match exactly that type
         */
        private val byConcreteType: Map<KType, List<Index>> = buildMap<KType, MutableList<Index>> {
            for ((d, components) in (listOf(components) + (parent?.componentsTable ?: emptyList())).withIndex())
                for ((i, c) in components.withIndex())
                    getOrPut(c.type) { ArrayList(1) }.add(Index(d, i))
        }

        /**
         * Cache for polymorphic type resolution
         */
        private val byType = mutableMapOf<KType, List<Index>>()

        /**
         * Finds all components that can provide the given type.
         * This includes both exact type matches and polymorphic subtypes.
         *
         * @param type The type to find providers for
         * @return List of indices pointing to components that provide this type
         */
        private fun providers(type: KType): List<Index> {
            return byType[type] ?: run {
                buildList {
                    for ((p, cs) in byConcreteType) {
                        if (p == type)
                            addAll(cs)
                        else
                            for (c in cs)
                                if (component(c).isSubtypeOf(type))
                                    add(c)
                    }
                    sort()
                }.also {
                    byType[type] = it
                }
            }
        }

        /**
         * Working list of component instantiations being built
         */
        private val instantiations = ArrayList<Instantiation>(components.size)

        /**
         * Maps component index to index (slot) of instance constructed
         * Value of -1 means not instantiated
         * Value of -(2+level) means currently being instantiated at level
         * Other values are indices into instantiations
         */
        private val componentSlot = components.mapTo(ArrayList<Int>(components.size)) { -1 }

        /**
         * Maps slot back to the component index
         */
        private val slotComponent = ArrayList<Int>(components.size)

        /**
         * If false object not yet scheduled for construction or fields/post construct not yet called
         */
        private val initialized = BooleanArray(components.size)

        /**
         * Looks up a component definition in the component hierarchy
         *
         * @param i Index pointing to a component's location
         * @return The component definition at that location
         */
        private fun component(i: Index) =
            if (i.depth == 0) components[i.index]
            else parents[i.depth - 1].components[i.index]

        /**
         * Resolves a dependency to the components that satisfy it.
         *
         * @param dep The dependency to resolve
         * @return List of indices pointing to components that satisfy the dependency
         * @throws IllegalStateException if the dependency cannot be satisfied or is ambiguous
         */
        private fun resolve(dep: Dependency): List<Index> = when (dep) {
            is Dependency.Value<*> -> emptyList()
            is Dependency.Multiple -> {
                val ps = providers(dep.type)
                if (ps.isEmpty() && dep.required)
                    throw IllegalStateException("Failed to find provider for ${dep.type}")
                ps
            }
            is Dependency.Single -> resolveSingleProvider(dep)
        }

        private fun resolveSingleProvider(dep: Dependency.Single): List<Index> =
            resolveSingleProvider(providers(dep.type), dep, this::component)

        /**
         * Wrapper for instantiate_ that returns the index
         */
        private fun instantiate(level: Int, index: Index, constructOnly: Boolean): Index {
            if (index.depth != 0)
                return index
            // First schedule constructor call
            instantiate_(level, index.index, true)
            if (!constructOnly) // then schedule any fields/post construct callback
                instantiate_(level, index.index, false)
            return index
        }

        /**
         * Adds a component to the instantiation list, resolving its constructor or field dependencies.
         * Handles dependency cycles by marking components being processed.
         *
         * @param level Current recursion level
         * @param i Index of the component to instantiate
         * @param constructOnly If true, only handle constructor injection; if false, handle field injection and post-construct
         */
        private fun instantiate_(level: Int, i: Int, constructOnly: Boolean) {
            val slot = componentSlot[i]
            if (slot >= 0 && (constructOnly || initialized[slot]))
                return
            val c = components[i]
            if (slot < -1) {
                val cycleComponents = componentSlot.withIndex()
                    .filter { (_, v) -> v <= -2 }
                    .sortedBy { (_, v) -> -v }
                    .map { (i, _) -> components[i] }

                val cycleDescription = cycleComponents.joinToString(" → ") { "${it.name} (${it.klass.simpleName})" }
                throw IllegalStateException("Circular dependency detected: $cycleDescription")
            }
            if (constructOnly) {
                componentSlot[i] = -(2 + level)
                val args = c.constructorArgs.map { resolveAll(level + 1, it, false) }
                val slot = slotComponent.size
                instantiations.add(Instantiation(-1, args))
                slotComponent.add(i)
                componentSlot[i] = slot
            } else {
                val args = c.fields.map { resolveAll(level + 1, it, true) }
                instantiations.add(Instantiation(slot, args))
                initialized[slot] = true
            }
        }

        /**
         * Resolves all components needed for a dependency and ensures they are instantiated
         */
        private fun resolveAll(level: Int, f: Dependency, constructOnly: Boolean) =
            resolve(f).map { instantiate(level, it, constructOnly) }

        /**
         * Creates a complete instantiation plan for all required components.
         *
         * This method:
         * 1. Instantiates all required components and their dependencies
         * 2. Resolves field dependencies for all instantiated components
         * 3. Compresses the component and instantiation lists to only include used components
         * 4. Remaps all indices to the compressed lists
         * 5. Creates a Plan with the final instantiation sequence
         *
         * @return A Plan that can be executed to create a Context with all components wired together
         */
        fun plan(): Plan {
            // First instantiate all required components (and their dependencies)
            for ((i, c) in components.withIndex())
                if (c.required)
                    instantiate(0, Index(0, i), false)

            // Resolve field dependencies for all instantiated components
            var prevInst = 0
            var nextSlot = 0
            while (prevInst != instantiations.size) {
                val range = prevInst until instantiations.size
                prevInst = instantiations.size
                for (i in range)
                    if (instantiations[i].slot == -1)
                        instantiate(0, Index(0, slotComponent[nextSlot++]), false)
            }

            // Compress component list to only include used components
            val compressed = slotComponent.map(components::get)

            check(compressed.size * 2 == instantiations.size)

            // Remap indices into original components to remapped indices
            fun remap(i: Index) = if (i.depth != 0) i else Index(0, componentSlot[i.index])
            fun remapAll(deps: List<List<Index>>) = deps.map { a -> a.map(::remap) }

            for ((i, inst) in instantiations.withIndex())
                instantiations[i] = inst.copy(args=remapAll(inst.args))

            instantiations.trimToSize()
            val shared = SharedContext(parents.firstOrNull(), compressed)
            return Plan(parents, shared, instantiations)
        }
    }
}
