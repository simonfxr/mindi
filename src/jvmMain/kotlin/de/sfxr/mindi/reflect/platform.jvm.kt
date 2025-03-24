package de.sfxr.mindi.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
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
    isAccessible = true
}

actual fun maybeExtendsAutoClosable(klass: KClass<*>): Boolean = klass.isSubclassOf(AutoCloseable::class)
