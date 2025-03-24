package de.sfxr.mindi

import java.util.concurrent.ConcurrentHashMap

internal actual fun getenv(name: String): String? = System.getenv(name)

internal actual fun <K : Any, V : Any> newConcurrentMap(): MutableMap<K, V> = ConcurrentHashMap()
