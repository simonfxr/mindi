package de.sfxr.mindi

import kotlin.jvm.JvmInline
import kotlin.reflect.*

/**
 * Wrapper around a KType to enable reified type arguments for supertype declarations.
 *
 * @param T The type parameter
 * @property type The underlying Kotlin type
 */
@JvmInline
value class TypeProxy<T> @PublishedApi internal constructor(val type: KType) {
    /**
     * Gets the Kotlin class of this type.
     */
    val klass: KClass<*> get() = type.classifier as? KClass<*> ?: error("Type classifier is not a class: ${type.classifier}")
}

/**
 * Creates a TypeProxy instance with the given reified type parameter.
 *
 * @param T The type to create a proxy for
 * @return A new TypeProxy instance for the specified type
 */
inline fun <reified T> TypeProxy(): TypeProxy<T> = TypeProxy<T>(typeOf<T>())

@Suppress("UNCHECKED_CAST")
infix fun <T: Any> Any?.cast(type: TypeProxy<T>): T = type.klass.cast(this) as T

@Suppress("UNCHECKED_CAST")
infix fun <T: Any> Any?.safeCast(type: TypeProxy<T>): T? = type.klass.safeCast(this) as? T
