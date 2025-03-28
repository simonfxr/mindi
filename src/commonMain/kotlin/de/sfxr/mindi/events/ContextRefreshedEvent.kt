package de.sfxr.mindi.events

import de.sfxr.mindi.Context

/**
 * Event published when the context is fully initialized and started.
 *
 * This event is fired after all components have been instantiated,
 * dependencies have been injected, and @PostConstruct methods have been called.
 * Similar to Spring's ContextRefreshedEvent.
 *
 * @property context The Context that has been fully initialized
 */
class ContextRefreshedEvent(val context: Context)