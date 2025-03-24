package de.sfxr.mindi

/**
 * Platform-specific function to get an environment variable.
 *
 * This function provides a consistent way to access environment variables
 * across different platforms (JVM, JS, Native).
 *
 * @param name The name of the environment variable
 * @return The value of the environment variable, or null if not found
 */
internal expect fun getenv(name: String): String?

/**
 * Platform-specific factory function for creating thread-safe concurrent maps.
 *
 * Creates a map implementation appropriate for the target platform:
 * - JVM: Uses ConcurrentHashMap
 * - JS/Native: Uses standard MutableMap (no actual thread-safety needed)
 *
 * @param K The type of keys in the map
 * @param V The type of values in the map
 * @return A new, empty concurrent map instance
 */
internal expect fun <K: Any, V: Any> newConcurrentMap(): MutableMap<K, V>