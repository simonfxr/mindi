package de.sfxr.mindi.events

import de.sfxr.mindi.Context

/**
 * Event published immediately before the context is closed and components are destroyed.
 *
 * This event is fired right before the context starts calling @PreDestroy methods
 * and releasing resources. Listeners can use this event to perform any required
 * cleanup operations that depend on other components still being available.
 * Similar to Spring's ContextClosedEvent.
 *
 * @property context The Context that is about to be closed
 */
class ContextClosedEvent(val context: Context)