package de.sfxr.mindi.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/**
 * Native-specific implementation to set the accessibility of a callable element.
 *
 * On native platforms, this is currently a no-op since Kotlin/Native's reflection
 * capabilities are more limited compared to the JVM.
 */
internal actual fun KCallable<*>.setAccessible() {}

actual fun maybeExtendsAutoClosable(klass: KClass<*>): Boolean = true
