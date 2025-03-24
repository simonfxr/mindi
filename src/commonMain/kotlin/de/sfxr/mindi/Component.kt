package de.sfxr.mindi

import de.sfxr.mindi.internal.compose
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Function type for setting values on component instances.
 * @param obj The target component instance
 * @param value The value to set
 */
typealias Sink = (obj: Any, value: Any?) -> Unit

/**
 * Function type for component lifecycle callbacks.
 * @param Any The component instance
 */
typealias Callback = (Any) -> Unit

/**
 * Represents a component in the dependency injection system.
 *
 * Components are the basic building blocks of the application and can be
 * instantiated and managed by the Context. Each component has dependencies,
 * lifecycle hooks, and event handling capabilities.
 *
 * In typical usage, you don't create Component instances directly - instead, you
 * annotate your classes with @Component and related annotations:
 *
 * ```kotlin
 * @Component
 * class UserService(
 *     // Constructor injection
 *     private val repository: UserRepository,
 *
 *     // Value injection
 *     @Value("\${user.cache.size:100}")
 *     private val cacheSize: Int
 * ) {
 *     // Field injection
 *     @Autowired
 *     private lateinit var eventPublisher: EventPublisher
 *
 *     // Lifecycle callback
 *     @PostConstruct
 *     fun initialize() {
 *         // Initialize after all dependencies are injected
 *     }
 *
 *     // Event listener
 *     @EventListener
 *     fun handleConfigChange(event: ConfigChangedEvent) {
 *         // React to events
 *     }
 *
 *     // Cleanup callback
 *     @PreDestroy
 *     fun cleanup() {
 *         // Release resources
 *     }
 * }
 * ```
 *
 * Components are created as singletons - only one instance exists per Context.
 * They are instantiated in dependency order, with circular dependencies detected
 * and reported as errors.
 *
 * @param T The type of the component
 * @property klass The Kotlin class of the component
 * @property primary Whether this component is the primary implementation for its type
 * @property construct Function to construct a new instance of the component
 * @property constructorArgs Dependencies needed for constructor injection
 * @property fields Dependencies needed for field/setter injection
 * @property setters Functions to set values on component instances
 * @property listenerArgs Event types this component listens for
 * @property listenerHandlers Functions to handle events
 * @property postConstruct Callback executed after construction and dependency injection
 * @property close Callback executed before component destruction
 * @property names Identifiers for this component (for qualification)
 * @property required Whether this component must be instantiated
 */
@ConsistentCopyVisibility
data class Component<out T: Any> internal constructor(
    val type: KType,
    val defaultName: String,
    val names: List<String> = emptyList(),
    val construct: Context.(List<Any?>) -> T,
    val constructorArgs: List<Dependency>,
    val superTypes: Collection<KType> = emptyList(),
    val primary: Boolean = false,
    val fields: List<Dependency> = emptyList(),
    val setters: List<Sink> = emptyList(),
    val listenerArgs: List<KType> = emptyList(),
    val listenerHandlers: List<Sink> = emptyList(),
    val postConstruct: Callback? = null,
    val close: Callback? = null,
    val required: Boolean = true,
) {

    /**
     * The raw class of this component type
     */
    val klass: KClass<*> get() = type.classifier as? KClass<*>
        ?: error("Type classifier is not a class: ${type.classifier}")

    /**
     * Returns the primary name of this component, or "unknown" if none exists
     */
    val name get() = names.getOrNull(0) ?: defaultName

    /**
     * Checks if this component's type is a subtype of the given type
     *
     * @param type The type to check against
     * @return True if this component is a subtype of the specified type
     */
    fun isSubtypeOf(type: KType): Boolean =
        type == this.type || type in superTypes || type == anyType

    /**
     * Creates a new Component with the given name as its primary name.
     *
     * @param name The primary name to set for this component
     * @return A new Component with the updated name
     */
    fun named(name: String): Component<T> {
        check(name != "")
        if (name in names) return this
        return copy(names=names + listOf(name))
    }

    /**
     * Creates a new Component with updated configuration flags.
     *
     * @param primary Whether this component is the primary implementation for its type
     * @param required Whether this component must be instantiated
     * @param names Identifiers for this component (for qualification)
     * @return A new Component with the updated configuration
     */
    fun with(primary: Boolean=this.primary, required: Boolean=this.required, names: List<String> = this.names): Component<T> =
        copy(primary=primary, required=required, names=names)

    /**
     * Adds a setter dependency to this component.
     *
     * @param A The type of the dependency to inject
     * @param type Type proxy representing the dependency type
     * @param required Whether this dependency is required (error if not found)
     * @param qualifier Optional qualifier name to select a specific dependency
     * @param setter Function to set the dependency on the component instance
     * @return A new Component with the added setter dependency
     */
    fun <A> setting(type: TypeProxy<A>, required: Boolean=true, qualifier: String?=null, setter: T.(A) -> Unit): Component<T> {
        @Suppress("UNCHECKED_CAST")
        return copy(
            fields=fields + listOf(Dependency(type.type, qualifier, required)),
            setters=setters + listOf { x, v -> (x as T).setter(v as A) })
    }

    /**
     * Adds a setter dependency to this component with reified type parameter.
     *
     * @param A The type of the dependency to inject
     * @param required Whether this dependency is required (error if not found)
     * @param qualifier Optional qualifier name to select a specific dependency
     * @param setter Function to set the dependency on the component instance
     * @return A new Component with the added setter dependency
     */
    inline fun <reified A> setting(required: Boolean=true, qualifier: String?=null, noinline setter: T.(A) -> Unit): Component<T> =
        setting(TypeProxy<A>(), required, qualifier, setter)

    /**
     * Qualifies a constructor dependency at the specified index with a qualifier name.
     *
     * This allows specific targeting of dependencies when multiple components
     * of the same type are available.
     *
     * @param index The zero-based index of the constructor argument to qualify
     * @param qualifier The qualifier name to apply to the dependency
     * @return A new Component with the updated qualified dependency
     * @throws IllegalArgumentException If the index is out of bounds or the dependency is not a Single
     */
    fun requireQualified(index: Int, qualifier: String): Component<T> {
        if (index < 0 || index >= constructorArgs.size) {
            error("Index $index out of bounds for constructorArgs with size ${constructorArgs.size}")
        }
        val arg = constructorArgs[index]
        if (arg !is Dependency.Single) {
            error("Constructor argument at index $index is not a Single dependency")
        }
        return copy(constructorArgs = constructorArgs.mapIndexed { i, dep ->
            if (i == index && dep is Dependency.Single) {
                Dependency.Single(dep.type, qualifier, dep.required)
            } else {
                dep
            }
        })
    }

    /**
     * Sets a constructor dependency at the specified index to use a value from a configuration source.
     *
     * This allows injecting values from environment variables, properties, or other sources
     * into constructor parameters without using annotations.
     *
     * The expression follows the same format as @Value annotation:
     * - "\${variable}" - Injects the value of the specified variable
     * - "\${variable:default}" - Injects the variable or the default value if not found
     * - "literal" - Directly parses the literal value as the target type
     *
     * Example:
     * ```kotlin
     * val configComponent = Component { maxConnections: Int, timeout: Long ->
     *     DatabaseConfig(maxConnections, timeout)
     * }
     * .requireValue(0, "\${DB_MAX_CONNECTIONS:10}")  // From DB_MAX_CONNECTIONS env var or 10 if not set
     * .requireValue(1, "\${db.timeout:5000}")        // From db.timeout property or 5000 if not set
     * ```
     *
     * @param index The zero-based index of the constructor argument to set as a value dependency
     * @param expression The value expression following @Value syntax
     * @return A new Component with the updated value dependency
     * @throws IllegalArgumentException If the index is out of bounds
     */
    fun requireValue(index: Int, expression: String): Component<T> {
        if (index < 0 || index >= constructorArgs.size) {
            error("Index $index out of bounds for constructorArgs with size ${constructorArgs.size}")
        }
        val arg = constructorArgs[index]
        val valueDependency = Dependency.parseValueExpression(TypeProxy(arg.type), expression)

        return copy(constructorArgs = constructorArgs.mapIndexed { i, dep ->
            if (i == index) valueDependency else dep
        })
    }

    /**
     * Adds an event listener to this component.
     *
     * @param A The type of event to listen for
     * @param type Type proxy representing the event type
     * @param handle Function to handle the event
     * @return A new Component with the added event listener
     */
    fun <A: Any> listening(type: TypeProxy<A>, handle: T.(A) -> Unit): Component<T> {
        @Suppress("UNCHECKED_CAST")
        return copy(
            listenerArgs=listenerArgs+listOf(type.type),
            listenerHandlers=listenerHandlers + listOf { x, v -> (x as T).handle(v as A) })
    }

    /**
     * Adds an event listener to this component with reified type parameter.
     *
     * @param A The type of event to listen for
     * @param handle Function to handle the event
     * @return A new Component with the added event listener
     */
    inline fun <reified A: Any> listening(noinline handle: T.(A) -> Unit): Component<T> =
        listening(TypeProxy<A>(), handle)

    /**
     * Adds an initialization callback to this component.
     * This function will be called after the component is constructed and all dependencies are injected.
     *
     * @param callback Function to execute during initialization
     * @return A new Component with the added initialization callback
     */
    fun onInit(callback: T.() -> Unit): Component<T> {
        @Suppress("UNCHECKED_CAST")
        return copy(postConstruct=compose(postConstruct) { v -> (v as T).callback() })
    }

    /**
     * Adds a cleanup callback to this component.
     * This function will be called before the component is destroyed.
     *
     * @param callback Function to execute during cleanup
     * @return A new Component with the added cleanup callback
     */
    fun onClose(callback: T.() -> Unit): Component<T> {
        @Suppress("UNCHECKED_CAST")
        return copy(close=compose(close) { v -> (v as T).callback() })
    }

    /**
     * Adds a supertype to a component, allowing it to be injected as that type.
     *
     * This is useful when you want a component to be injectable as a type that isn't
     * directly in its inheritance hierarchy, but is compatible with the component.
     *
     * @param type The type proxy representing the supertype
     * @return A new component with the added supertype
     */
    fun withSuperType(type: TypeProxy<in T>): Component<T> =
        if (isSubtypeOf(type.type)) this else copy(superTypes=superTypes + listOf(type.type))

    companion object  {

        /**
         * Creates a Component with reified type parameter and explicit dependencies.
         *
         * @param args Dependencies needed for constructor injection
         * @param name Optional name for the component (defaults to lowercased class name)
         * @param construct Function to construct a new instance of the component
         * @return A new Component instance
         */
        inline fun <reified T: Any> define(vararg args: Dependency, name: String? = null, noinline construct: Context.(List<Any?>) -> T): Component<T> =
            define(TypeProxy<T>(), args.toList(), name, construct)

        /**
         * Creates a Component with explicit type and dependencies.
         *
         * @param type Type proxy representing the component type
         * @param args Dependencies needed for constructor injection
         * @param name Optional name for the component (defaults to lowercased class name)
         * @param construct Function to construct a new instance of the component
         * @return A new Component instance
         */
        fun <T: Any> define(type: TypeProxy<T>, args: List<Dependency>, name: String? = null, construct: Context.(List<Any?>) -> T): Component<T> {
            return define(type.type, args, name, construct)
        }

        /**
         * Creates a Component without dependencies.
         *
         * @param f A function that creates an instance of T without any dependencies
         * @return A new Component instance
         */
        inline operator fun <reified T: Any> invoke(noinline f: () -> T): Component<T> =
            define(typeOf<T>(), { f() })

        /**
         * Creates a Component with a single dependency.
         *
         * @param f A function that creates an instance of T with one dependency
         * @return A new Component instance
         */
        inline operator fun <reified T: Any, reified A> invoke(noinline f: (A) -> T): Component<T> =
            define(typeOf<T>(), { f(it[0] as A) }, dep<A>())

        /**
         * Creates a Component with two dependencies.
         *
         * @param f A function that creates an instance of T with two dependencies
         * @return A new Component instance
         */
        inline operator fun <reified T: Any, reified A, reified B> invoke(noinline f: (A, B) -> T): Component<T> =
            define(typeOf<T>(), { f(it[0] as A, it[1] as B) }, dep<A>(), dep<B>())

        /**
         * Creates a Component with three dependencies.
         *
         * @param f A function that creates an instance of T with three dependencies
         * @return A new Component instance
         */
        inline operator fun <reified T: Any, reified A, reified B, reified C> invoke(
            noinline f: (A, B, C) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C) },
            dep<A>(), dep<B>(), dep<C>()
        )

        /**
         * Creates a Component with four dependencies.
         *
         * @param f A function that creates an instance of T with four dependencies
         * @return A new Component instance
         */
        inline operator fun <reified T: Any, reified A, reified B, reified C, reified D> invoke(
            noinline f: (A, B, C, D) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>()
        )

        /**
         * Creates a Component with five dependencies.
         *
         * @param f A function that creates an instance of T with five dependencies
         * @return A new Component instance
         */
        inline operator fun <reified T: Any, reified A, reified B, reified C, reified D, reified E> invoke(
            noinline f: (A, B, C, D, E) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>()
        )

        /**
         * Creates a Component with six dependencies.
         *
         * @param f A function that creates an instance of T with six dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F
        > invoke(
            noinline f: (A, B, C, D, E, F) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>()
        )

        /**
         * Creates a Component with seven dependencies.
         *
         * @param f A function that creates an instance of T with seven dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G
        > invoke(
            noinline f: (A, B, C, D, E, F, G) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>()
        )

        /**
         * Creates a Component with eight dependencies.
         *
         * @param f A function that creates an instance of T with eight dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>()
        )

        /**
         * Creates a Component with nine dependencies.
         *
         * @param f A function that creates an instance of T with nine dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>()
        )

        /**
         * Creates a Component with ten dependencies.
         *
         * @param f A function that creates an instance of T with ten dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>()
        )

        /**
         * Creates a Component with eleven dependencies.
         *
         * @param f A function that creates an instance of T with eleven dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J, reified K
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J, K) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J, it[10] as K) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>(), dep<K>()
        )

        /**
         * Creates a Component with twelve dependencies.
         *
         * @param f A function that creates an instance of T with twelve dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J, reified K, reified L
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J, K, L) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J, it[10] as K, it[11] as L) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>(), dep<K>(), dep<L>()
        )

        /**
         * Creates a Component with thirteen dependencies.
         *
         * @param f A function that creates an instance of T with multiple dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J, reified K, reified L, reified M
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J, K, L, M) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J, it[10] as K, it[11] as L, it[12] as M) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>(), dep<K>(), dep<L>(), dep<M>()
        )

        /**
         * Creates a Component with fourteen dependencies.
         *
         * @param f A function that creates an instance of T with multiple dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J, reified K, reified L, reified M, reified N
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J, it[10] as K, it[11] as L, it[12] as M, it[13] as N) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>(), dep<K>(), dep<L>(), dep<M>(), dep<N>()
        )

        /**
         * Creates a Component with fifteen dependencies.
         *
         * @param f A function that creates an instance of T with multiple dependencies
         * @return A new Component instance
         */
        inline operator fun <
            reified T: Any,
            reified A, reified B, reified C, reified D, reified E, reified F, reified G, reified H,
            reified I, reified J, reified K, reified L, reified M, reified N, reified O
        > invoke(
            noinline f: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) -> T
        ): Component<T> = define(
            typeOf<T>(),
            { f(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E, it[5] as F, it[6] as G, it[7] as H, it[8] as I, it[9] as J, it[10] as K, it[11] as L, it[12] as M, it[13] as N, it[14] as O) },
            dep<A>(), dep<B>(), dep<C>(), dep<D>(), dep<E>(), dep<F>(), dep<G>(), dep<H>(), dep<I>(), dep<J>(), dep<K>(), dep<L>(), dep<M>(), dep<N>(), dep<O>()
        )

        @PublishedApi
        internal fun <T: Any> define(type: KType, construct: Context.(List<Any?>) -> T, vararg args: Dependency): Component<T> =
            define(type, args.toList(), null, construct)

        internal fun <T: Any> define(type: KType, args: List<Dependency>, name: String?, construct: Context.(List<Any?>) -> T): Component<T> {
            return Component<T>(type, defaultName(type), namesOf(name), construct, args)
        }

        @PublishedApi
        internal fun dep(t: KType) = Dependency(t, qualifier=null, required=!t.isMarkedNullable)

        @PublishedApi
        internal inline fun <reified T> dep() = dep(typeOf<T>())

        internal fun defaultName(klass: KClass<*>): String =
            (klass.simpleName?.replaceFirstChar { it.lowercase() } ?: "").ifEmpty { "unknown" }

        internal fun defaultName(type: KType): String = defaultName(type.classifier as KClass<*>)

        internal fun namesOf(name: String?): List<String> = if (name.isNullOrEmpty()) emptyList() else listOf(name)

        private val anyType = typeOf<Any>()
    }
}

inline fun <T, reified U: Any> Component<T>.withSuperType(): Component<T> where T: U =
    withSuperType(TypeProxy<U>())
