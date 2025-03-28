package de.sfxr.mindi.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Platform-specific implementation to set the accessibility of a callable element.
 *
 * This is needed because different platforms have different mechanisms for handling
 * accessibility of private/protected members.
 */
internal expect fun KCallable<*>.setAccessible()

/**
 * Platform-specific check to determine if a class extends AutoCloseable.
 *
 * This is used to automatically detect resources that should be closed
 * during context shutdown.
 *
 * @param klass The class to check
 * @return True if the class extends AutoCloseable, false otherwise
 */
internal expect fun maybeExtendsAutoClosable(klass: KClass<*>): Boolean

/**
 * Platform-specific function to get the qualified or simple name of a class.
 *
 * This function provides a consistent way to get class names across different platforms
 * (JVM, JS, Native) where the qualifiedName property might not be available.
 *
 * @return The qualified name of the class if available, or the simple name as fallback
 */
internal expect fun KClass<*>.qualifiedOrSimpleName(): String
