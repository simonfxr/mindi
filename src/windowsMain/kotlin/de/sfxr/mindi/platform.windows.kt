package de.sfxr.mindi

import kotlinx.cinterop.*
import platform.windows.*

/**
 * Windows implementation of environment variable resolution.
 * Uses the Win32 GetEnvironmentVariable API.
 *
 * @param name The name of the environment variable to retrieve
 * @return The value of the environment variable, or null if not found
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformGetenv(name: String): String? = memScoped {
    // First query the size needed for the buffer
    val size = GetEnvironmentVariableW(name, null, 0u)

    // If size is 0, the variable doesn't exist
    if (size == 0u) return null

    // Allocate a buffer of the required size
    val buffer = allocArray<WCHARVar>(size.toInt())

    // Get the variable value
    val result = GetEnvironmentVariableW(name, buffer, size)

    // If result is 0, an error occurred
    if (result == 0u) return null

    // Convert to Kotlin string and return
    return buffer.toKString()
}