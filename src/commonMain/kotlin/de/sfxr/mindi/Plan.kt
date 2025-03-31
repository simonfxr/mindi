package de.sfxr.mindi

import de.sfxr.mindi.internal.sizeOr
import de.sfxr.mindi.reflect.qualifiedOrSimpleName
import kotlin.reflect.KType

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

        internal inline fun resolveSingleProvider(c: Component<*>?, all: List<Index>, type: KType, qual: Any?, required: Boolean, component: (Index) -> Component<*>): List<Index> {
            if (all.size == 1)
                return all
            else if (all.isEmpty() && required)
                throw IllegalStateException("Failed to find provider for $type${c?.let { " required by ${it.name}: ${it.klass}" } ?: ""}")
            else if (qual != null)
                throw IllegalStateException("Failed to find unique provider qualified '$qual' for $type${c?.let { " required by ${it.name}: ${it.klass}" } ?: ""}")

            // Iterate dependencies level by level
            // Components at level k shadow all components at level > k
            // Find unique primary component at level k or unique non primary at level k
            // if neither is found, go to next level
            var depth = 0
            while (all.isNotEmpty() && depth != Int.MAX_VALUE) {
                val currentProviders = all.filter { it.depth == depth }
                val providers = currentProviders.filter { i -> component(i).primary }.takeIf { it.isNotEmpty() } ?: currentProviders
                if (providers.size > 1)
                    throw IllegalStateException("Providers for dependency $type are ambiguous")
                if (providers.size == 1)
                    return providers
                depth = all.minOf { if (it.depth > depth) it.depth else Int.MAX_VALUE }
            }

            if (required)
                throw IllegalStateException("Failed to find provider for $type")

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
                is Dependency.Value<*> -> resolver.resolveValue(a, c).getOrThrow()
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
        private fun allProviders(type: KType): List<Index> {
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

        private fun providers(type: KType, qual: Any?) =
            allProviders(type).let { ps -> if (qual == null) ps else ps.filter { component(it).isQualifiedBy(qual) } }

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
        private val linked = BooleanArray(components.size)

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
        private fun resolve(c: Component<*>, dep: Dependency): List<Index> = when (dep) {
            is Dependency.Value<*> -> emptyList()
            is Dependency.Multiple<*> -> {
                val ps = providers(dep.type, dep.qualifier)
                if (ps.isEmpty() && dep.required)
                    throw IllegalStateException("Failed to find provider for ${dep.type} required by ${c.name}: ${c.klass}")
                ps
            }
            is Dependency.Single -> resolveSingleProvider(c, dep)
        }

        private fun resolveSingleProvider(c: Component<*>, dep: Dependency.Single): List<Index> =
            resolveSingleProvider(c, providers(dep.type, dep.qualifier), dep.type, dep.qualifier, dep.required, this::component)

        private enum class Phase {
            CONSTRUCT, LINK, INIT
        }

        /**
         * Wrapper for instantiate_ that returns the index
         */
        private fun instantiate(level: Int, index: Index, phase: Phase): Index {
            if (index.depth != 0)
                return index
            instantiate_(level, index.index, Phase.CONSTRUCT)
            if (phase.ordinal >= Phase.LINK.ordinal) instantiate_(level, index.index, Phase.LINK)
            if (phase.ordinal >= Phase.INIT.ordinal) instantiate_(level, index.index, Phase.INIT)
            return index
        }

        /**
         * Adds a component to the instantiation list, resolving its constructor or field dependencies.
         * Handles dependency cycles by marking components being processed.
         *
         * @param level Current recursion level
         * @param i Index of the component to instantiate
         * @param phase phase
         */
        private fun instantiate_(level: Int, i: Int, phase: Phase) {
            val curSlot = componentSlot[i]
            val currentPhase = when {
                initialized[i] -> Phase.INIT
                linked[i] -> Phase.LINK
                curSlot >= 0 -> Phase.CONSTRUCT
                else -> null
            }
            if (currentPhase != null && currentPhase.ordinal >= phase.ordinal)
                return
            val c = components[i]
            if (curSlot < -1) {
                fun phaseAt(k: Int): String {
                    return if (initialized[k]) "init"
                    else if (linked[k]) "fields"
                    else "construct"
                }
                // Find all components currently being instantiated (they form the cycle)
                val cycleComponents: List<Pair<Component<*>, String>> = componentSlot.withIndex()
                    .filter { (_, v) -> v <= curSlot } // All components being instantiated (have negative values less than -1)
                    .sortedBy { (_, v) -> -v }    // Sort by slot values to get dependency order
                    .map { (i, _) -> components[i] to phaseAt(i) } + listOf(c to (when(phase) {
                        Phase.CONSTRUCT -> "construct"
                        Phase.LINK -> "fields"
                        Phase.INIT -> "init"
                    }))

                val cycleDescription = cycleComponents.joinToString(" → ") { (comp, phase) ->
                    "$phase ${comp.name}: ${comp.klass.qualifiedOrSimpleName()}"
                }
                throw DependencyCycleException(cycleComponents, "Circular dependency detected: $cycleDescription")
            }
            componentSlot[i] = -(2 + level)
            val slot: Int
            when (phase) {
                Phase.CONSTRUCT -> {
                    val args = c.constructorArgs.map { resolveAll(level + 1, c, it, Phase.INIT) }
                    slot = slotComponent.size
                    instantiations.add(Instantiation(-1, args))
                    slotComponent.add(i)
                    componentSlot[i] = slot
                    if (c.fields.isEmpty()) {
                        linked[i] = true
                        initialized[i] = c.postConstruct == null
                    }
                }
                Phase.LINK -> {
                    slot = curSlot
                    if (c.fields.isNotEmpty()) {
                        val args = c.fields.map { resolveAll(level + 1, c, it, Phase.CONSTRUCT) }
                        instantiations.add(Instantiation(slot, args))
                    }
                    linked[i] = true
                    initialized[i] = c.postConstruct == null
                }
                Phase.INIT -> {
                    slot = curSlot
                    c.fields.forEach { resolveAll(level + 1, c, it, Phase.LINK) }
                    instantiations.add(Instantiation(-slot - 2, emptyList()))
                    initialized[i] = true
                }
            }
            componentSlot[i] = slot
        }

        /**
         * Resolves all components needed for a dependency and ensures they are instantiated
         */
        private fun resolveAll(level: Int, c: Component<*>, f: Dependency, phase: Phase) =
            resolve(c, f).map { instantiate(level, it, phase) }

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
                    instantiate(0, Index(0, i), Phase.CONSTRUCT)

            // Resolve field dependencies for all instantiated components
            var prevInst = 0
            var nextSlot = 0
            while (prevInst != instantiations.size) {
                val range = prevInst until instantiations.size
                prevInst = instantiations.size
                for (i in range)
                    if (instantiations[i].slot == -1)
                        instantiate(0, Index(0, slotComponent[nextSlot++]), Phase.INIT)
            }

            // Compress component list to only include used components
            val compressed = slotComponent.map(components::get)

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
