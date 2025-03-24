package de.sfxr.mindi

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * A thread-safe concurrent map implementation for Kotlin Native.
 *
 * This implementation uses a reentrant lock from kotlinx.atomicfu.locks to provide
 * thread safety around a standard mutable map.
 *
 * @param K The type of keys in the map
 * @param V The type of values in the map
 */
internal class ConcurrentMap<K : Any, V : Any> : MutableMap<K, V> {
    private val lock = ReentrantLock()
    private val delegate = mutableMapOf<K, V>()

    override val size: Int
        get() = lock.withLock { delegate.size }

    override fun containsKey(key: K): Boolean = lock.withLock { delegate.containsKey(key) }

    override fun containsValue(value: V): Boolean = lock.withLock { delegate.containsValue(value) }

    override fun get(key: K): V? = lock.withLock { delegate[key] }

    override fun isEmpty(): Boolean = lock.withLock { delegate.isEmpty() }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = lock.withLock { HashSet(delegate.entries) }

    override val keys: MutableSet<K>
        get() = lock.withLock { HashSet(delegate.keys) }

    override val values: MutableCollection<V>
        get() = lock.withLock { ArrayList(delegate.values) }

    override fun clear() = lock.withLock { delegate.clear() }

    override fun put(key: K, value: V): V? = lock.withLock { delegate.put(key, value) }

    override fun putAll(from: Map<out K, V>) = lock.withLock { delegate.putAll(from) }

    override fun remove(key: K): V? = lock.withLock { delegate.remove(key) }
}