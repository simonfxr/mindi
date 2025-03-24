package de.sfxr.mindi

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv as posixGetenv

/**
 * POSIX implementation of environment variable resolution.
 * Uses the POSIX getenv function from the platform.posix package.
 *
 * @param name The name of the environment variable to retrieve
 * @return The value of the environment variable, or null if not found
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformGetenv(name: String): String? = posixGetenv(name)?.toKString()