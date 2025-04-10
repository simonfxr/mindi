package de.sfxr.mindi

/**
 * Interface for interrupting the instantiation process of a Context.
 *
 * Implement this interface and pass it to Context.instantiate to control the
 * instantiation process. If shouldStop returns true, the instantiation process
 * will be interrupted, and the partial context will be properly torn down.
 */
interface StopToken {
    /** Returns true if the instantiation process should be interrupted. */
    val shouldStop: Boolean
}