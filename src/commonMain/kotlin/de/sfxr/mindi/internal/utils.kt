package de.sfxr.mindi.internal

import de.sfxr.mindi.Callback

/**
 * Composes two nullable callback functions into one.
 * The composed function will call both functions in sequence (first f, then g).
 *
 * @param f First function to call
 * @param g Second function to call
 * @return Composed function, or the non-null function if one is null
 */
internal fun compose(f: Callback?, g: Callback?): Callback {
    if (g == null) return f!!
    if (f == null) return g
    return { f(it); g(it) }
}

/**
 * Optimizes an ArrayList by either returning it as is, returning empty list if empty,
 * or trimming its capacity to size for better memory usage.
 *
 * @param T The element type
 * @return The optimized list (either the original trimmed list or an empty list)
 */
internal fun <T> ArrayList<T>.compact(): List<T> {
    if (isEmpty()) return emptyList()
    if (size == 1) return listOf(get(0))
    trimToSize()
    return this
}

/**
 * Associates elements to key-value pairs, ensuring unique keys by appending
 * numbers if collisions occur.
 *
 * @param T The element type
 * @param V The value type in the resulting map
 * @param transform Function to convert elements to key-value pairs
 * @return Map with unique keys
 */
internal inline fun <T, V> Iterable<T>.associateUnique(transform: (T) -> Pair<String, V>): LinkedHashMap<String, V> {
    val m = LinkedHashMap<String, V>()
    return associateTo(m) {
        val (k0, v) = transform(it)
        var k = k0
        var i = 0
        while (k in m) {
            i++
            k = "${k0}_$i"
        }
        k to v
    }
}

/**
 * Returns the size of a collection, or a default value if the iterable is not a collection.
 *
 * @param n Default size value to return if size cannot be determined
 * @return The size of the collection or the default value
 */
internal fun Iterable<*>.sizeOr(n: Int = 0) = (this as? Collection<*>)?.size ?: n
