package de.sfxr.mindi

/**
 * Represents the instantiation plan for a component.
 *
 * This class tracks all dependencies that will be injected into a component,
 * both constructor arguments and field values, using indices that point to
 * where in the component hierarchy each dependency can be found.
 *
 * @property args Constructor argument dependencies as lists of indices
 */
internal data class Instantiation(val slot: Int, val args: List<List<Index>>) {
    /**
     * Index into the component hierarchy for dependency lookup.
     *
     * @property depth Depth in the context hierarchy (0 = current context, 1 = parent, etc)
     * @property index Index in the components list at the given depth
     */
    data class Index(val depth: Int, val index: Int): Comparable<Index> {
        override fun compareTo(other: Index): Int {
            var r = depth.compareTo(other.depth)
            if (r != 0) return r
            r = index.compareTo(other.index)
            return r
        }
    }
}
