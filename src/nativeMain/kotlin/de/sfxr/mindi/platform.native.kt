package de.sfxr.mindi

/**
 * Native implementation of environment variable resolution.
 *
 * This is the common native interface, with actual implementations in:
 * - posixMain: POSIX implementation using platform.posix.getenv
 * - windowsMain: Windows implementation using GetEnvironmentVariable
 *
 * @param name The name of the environment variable to retrieve
 * @return The value of the environment variable, or null if not found
 */
internal actual fun getenv(name: String): String? = platformGetenv(name)

/**
 * Platform-specific implementation of getenv.
 * This is defined in posixMain and windowsMain source sets.
 */
internal expect fun platformGetenv(name: String): String?

/**
 * Native implementation of concurrent map using AtomicFU locks.
 *
 * @return A new thread-safe ConcurrentMap instance
 */
internal actual fun <K : Any, V : Any> newConcurrentMap(): MutableMap<K, V> = ConcurrentMap()