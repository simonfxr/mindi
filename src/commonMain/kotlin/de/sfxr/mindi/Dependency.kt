package de.sfxr.mindi

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Represents a dependency that a component has on other components or values.
 *
 * Dependencies can be of three types:
 * - Value: External values injected from environment variables, configuration or custom sources
 * - Single: Single component instance of a specific type (most common)
 * - Multiple: Multiple components of a specific type (injected as a Map<String, Component>)
 *
 * In typical usage, dependencies are created automatically from your annotated classes,
 * but you should understand how they work to use the library effectively:
 *
 * 1. Single dependencies are the most common and created from @Autowired fields/constructors:
 *    ```kotlin
 *    @Component
 *    class UserService(
 *        // This creates a Single dependency on UserRepository
 *        private val repository: UserRepository
 *    )
 *    ```
 *
 * 2. Value dependencies are created from @Value annotations:
 *    ```kotlin
 *    @Component
 *    class CacheConfig(
 *        // This creates a Value dependency that resolves from environment
 *        @Value("\${cache.size:100}")
 *        val maxSize: Int
 *    )
 *    ```
 *
 * 3. Multiple dependencies are created when injecting a Map:
 *    ```kotlin
 *    @Component
 *    class EventManager {
 *        // This creates a Multiple dependency that collects all EventHandlers
 *        @Autowired
 *        lateinit var handlers: Map<String, EventHandler>
 *    }
 *    ```
 *
 * The framework automatically resolves these dependencies during context creation,
 * making the appropriate instances available to your components.
 *
 * @property klass The Kotlin class of the dependency
 */
sealed class Dependency {
    abstract val type: KType

    /**
     * Gets the raw class of this dependency
     */
    val klass: KClass<*> get() = type.classifier as? KClass<*> ?: error("Type classifier is not a class: ${type.classifier}")

    /**
     * Dependency on an external value like environment variable or property.
     *
     * @param T The type of the value to be resolved
     * @property type The expected type of the value including generic type information
     * @property variable The environment variable name to resolve
     * @property default Optional default value if variable is not found
     */
    @ConsistentCopyVisibility
    data class Value<T: Any> internal constructor(
        val typeProxy: TypeProxy<T>,
        val variable: String,
        val default: T?
    ): Dependency() {
        override val type: KType get() = typeProxy.type
    }

    /**
     * Dependency on a single component of a specific type.
     *
     * @property type The required component type including generic type information
     * @property qualifier Optional qualifier to disambiguate between multiple components of the same type
     * @property required Whether this dependency is required (error if not found)
     */
    @ConsistentCopyVisibility
    data class Single internal constructor(
        override val type: KType,
        val qualifier: Any?,
        val required: Boolean
    ): Dependency()

    /**
     * Dependency on multiple components of a specific type.
     * Components will be injected as a Map<String, Component>.
     *
     * @property type The required component type including generic type information
     * @property required Whether this dependency is required (error if not found)
     */
    @ConsistentCopyVisibility
    data class Multiple internal constructor(
        override val type: KType,
        val required: Boolean
    ): Dependency()

    companion object {
        /**
         * Creates a Value dependency for a given expression string and target type.
         *
         * Handles two formats:
         * 1. ${variable} or ${variable:default} - Resolves from a ValueResolver
         * 2. literal value - Parses directly as the target type
         *
         * @param type The target type including generic information
         * @param expression The input string to parse
         * @return A Value dependency that will resolve the value
         */
        fun <T: Any> parseValueExpression(type: TypeProxy<T>, expression: String): Value<T> {
            if (expression.startsWith("\${") && expression.endsWith("}")) {
                val content = expression.substring(2, expression.length - 1)
                val parts = content.split(":", limit = 2)
                val variable = parts[0].trim()
                check(variable.isNotEmpty()) { "variable may not be blank" }
                val defaultValue: T? = parts.getOrNull(1)?.let { ValueResolver.Empty.parseValue(type, it) }
                return Value(type, variable, defaultValue)
            } else {
                val v = ValueResolver.Empty.parseValue(type, expression)
                return Value(type, "", v)
            }
        }

        internal fun <T: Any> parseValueExpressionFor(
            type: TypeProxy<T>, expression: String,
            componentName: String?, componentType: KType,
        ): Value<T> {
            try {
                return parseValueExpression(type, expression)
            } catch (e: Exception) {
                throw RuntimeException("failed to parse value expression for ${componentName}: ${componentType}: $expression", e)
            }
        }
    }
}

/**
 * A dummy map implementation used for type checking during dependency resolution.
 *
 * This map is only used for checking if a type is assignable from Map<String, Any>
 * during dependency resolution, particularly for identifying Multiple dependencies
 * that should be injected as maps. The methods are intentionally not implemented
 * as they are never called - only isInstance() is used on the class.
 */
internal val dummyMap: Map<String, Any?> = object : Map<String, Any> {
    override val size get() = 0
    override val keys get() = TODO("Not yet implemented")
    override val values get() = TODO("Not yet implemented")
    override val entries get() = TODO("Not yet implemented")
    override fun isEmpty() = TODO("Not yet implemented")
    override fun containsKey(key: String) = TODO("Not yet implemented")
    override fun containsValue(value: Any) = TODO("Not yet implemented")
    override fun get(key: String) = TODO("Not yet implemented")
}

/**
 * Creates a dependency for a type with optional qualifier and required flags.
 *
 * @param type The constructor/setter argument type including generic type information
 * @param qualifier Optional qualifier to disambiguate between multiple components of the same type
 * @param required Whether this dependency must be fulfilled (defaults to true)
 * @return The appropriate Dependency type based on the input type
 */
fun Dependency(type: KType, qualifier: Any? = null, required: Boolean = true): Dependency {
    // Special handling for Map<String, T> types which become Multiple dependencies
    val classifier = type.classifier
    if (classifier is KClass<*> && classifier.isInstance(dummyMap)) {
        type.arguments.takeIf { it.size == 2 }?.let { (kt, vt) ->
            if (kt.type == typeOf<String>()) {
                val valueType = vt.type
                if (valueType != null) {
                    return Dependency.Multiple(valueType, required)
                }
            }
        }
    }

    // Standard dependency
    return Dependency.Single(type, qualifier, required)
}
