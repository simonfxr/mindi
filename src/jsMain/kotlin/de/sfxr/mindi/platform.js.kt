package de.sfxr.mindi

internal actual fun getenv(name: String): String? = null

internal actual fun <K : Any, V : Any> newConcurrentMap(): MutableMap<K, V> = mutableMapOf()
