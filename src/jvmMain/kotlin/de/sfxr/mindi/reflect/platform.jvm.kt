package de.sfxr.mindi.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

/**
 * JVM-specific implementation to set the accessibility of a callable element.
 *
 * On the JVM, this uses kotlin.reflect.jvm.isAccessible property to make
 * the callable accessible regardless of its visibility modifier.
 *
 */
internal actual fun KCallable<*>.setAccessible() {
    if (visibility != KVisibility.PUBLIC)
        isAccessible = true
}

actual fun maybeExtendsAutoClosable(klass: KClass<*>): Boolean = klass.isSubclassOf(AutoCloseable::class)

/**
 * JVM-specific implementation to get the qualified or simple name of a class.
 *
 * On the JVM, we can directly use the qualifiedName property which is always available.
 */
internal actual fun KClass<*>.qualifiedOrSimpleName(): String = qualifiedName ?: simpleName ?: "<anonymous>"
