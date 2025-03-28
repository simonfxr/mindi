package de.sfxr.mindi.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/**
 * JavaScript-specific implementation to set the accessibility of a callable element.
 *
 * On JS, this is a no-op since JavaScript doesn't have the same access modifiers
 * as JVM languages. All properties are accessible in JavaScript.
 *
 */
internal actual fun KCallable<*>.setAccessible() {}

actual fun maybeExtendsAutoClosable(klass: KClass<*>): Boolean = true

/**
 * JavaScript-specific implementation to get the qualified or simple name of a class.
 *
 * On JS, the qualifiedName property might not be reliable, so we fallback to simpleName.
 */
internal actual fun KClass<*>.qualifiedOrSimpleName(): String = simpleName ?: "<anonymous>"
