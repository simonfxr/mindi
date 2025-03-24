package de.sfxr.mindi

import kotlin.time.Duration

/**
 * Interface for resolving external values for injection.
 *
 * ValueResolvers are used to provide values for @Value annotations
 * from external sources like environment variables, property files,
 * or other configuration mechanisms.
 */
interface ValueResolver {
    /**
     * Resolves a key to its corresponding value.
     *
     * @param key The key to resolve
     * @return The resolved value, or null if the key cannot be resolved
     */
    fun resolve(key: String): Any?

    /**
     * Parses a string value to a specific type.
     *
     * @param type Type information for the target type
     * @param value String value to parse
     * @return The parsed value of the specified type
     * @throws IllegalArgumentException if the type is not supported
     */
    fun <T: Any> parseValue(type: TypeProxy<T>, value: String): T = when (type.klass) {
        String::class -> value
        Byte::class -> value.toByte()
        Short::class -> value.toShort()
        Int::class -> value.toInt()
        Long::class -> value.toLong()
        Float::class -> value.toFloat()
        Double::class -> value.toDouble()
        Boolean::class -> value.toBoolean()
        Duration::class -> Duration.parse(value)
        else -> throw IllegalArgumentException("Cannot parse to type ${type.klass}")
    } cast type

    object Empty : ValueResolver {
        override fun resolve(key: String): Nothing? = null
    }

    companion object {
        /**
         * Creates a ValueResolver from a function.
         *
         * Utility method for easily creating ValueResolver instances without
         * implementing the full interface.
         *
         * @param resolve Function that takes a key and returns the resolved value or null
         * @return A ValueResolver that delegates to the provided function
         */
        fun with(resolve: (String) -> Any?): ValueResolver =
            object : ValueResolver {
                override fun resolve(key: String) = resolve(key)
            }

        /**
         * Combines multiple ValueResolvers into a single resolver.
         *
         * When resolving a key, each resolver is tried in order until one returns a non-null value.
         * If no resolver can resolve the key, null is returned.
         *
         * @param resolvers The ValueResolvers to combine
         * @return A composite ValueResolver that tries each provided resolver in sequence
         */
        fun compose(vararg resolvers: ValueResolver): ValueResolver = when (resolvers.size) {
            0 -> Empty
            1 -> resolvers[0]
            else -> with { resolvers.fold(null) { r, f -> r ?: f.resolve(it) } }
        }
    }
}

/**
 * Resolves a variable from a ValueResolver and converts it to the target type.
 *
 * This function handles:
 * - Using default values when the variable isn't found
 * - Type checking and conversion
 * - Error handling
 *
 * @param T The target type
 * @param valueDependency The Value dependency to resolve
 * @return Result containing either the resolved value or an error
 */
fun <T: Any> ValueResolver.resolveValue(valueDependency: Dependency.Value<T>): Result<T> {
    val klass = valueDependency.klass
    val variable = valueDependency.variable
    val defaultValue = valueDependency.default

    val v = if (variable != "") resolve(variable) else null

    if (v === null && defaultValue != null)
        return Result.success(defaultValue)

    if (v === null)
        return Result.failure(IllegalStateException("failed to resolve variable $variable"))

    val coerced = v safeCast valueDependency.typeProxy
    if (coerced != null)
        return Result.success(coerced)

    if (v !is String)
        return Result.failure(IllegalArgumentException("Wrong type, cannot parse ${v::class} to $klass"))

    return runCatching { parseValue(valueDependency.typeProxy, v) }
}

