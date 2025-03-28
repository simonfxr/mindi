package de.sfxr.mindi

import de.sfxr.mindi.reflect.qualifiedOrSimpleName

/**
 * Exception thrown when a circular dependency is detected in the component graph.
 *
 * @property cycle List of components that form the dependency cycle, paired with the phase when the dependency occurs
 * @property message Detailed error message describing the cycle
 */
class DependencyCycleException(
    val cycle: List<Pair<Component<*>, String>>,
    override val message: String
) : IllegalStateException(message) {
    /**
     * Returns a string representation of the dependency cycle.
     *
     * @return A formatted string showing the components involved in the cycle and their phases
     */
    override fun toString(): String = message

    /**
     * Returns a detailed visualization of the dependency cycle.
     *
     * This method creates a more structured representation of the cycle,
     * showing component names, types, and at which phase the dependency occurs.
     *
     * @return A multi-line string representing the cycle with one component per line
     */
    fun getDetailedCycleRepresentation(): String {
        val result = StringBuilder("Circular dependency detected:\n")

        cycle.forEachIndexed { index, (component, phase) ->
            result.append("${index + 1}. ${component.name} (${component.klass.qualifiedOrSimpleName()})")

            if (phase != "construct") {
                result.append(" during $phase phase")
            }

            // Add dependency arrow for all except the last element
            if (index < cycle.size - 1) {
                result.append("\n   ↓ depends on\n")
            } else {
                // For the last element, show arrow back to the first element
                result.append("\n   ↓ depends on #1 (${cycle.first().first.name})")
            }
        }

        result.append("\n\nHint: To resolve this circular dependency, consider:\n")
        result.append("- Using constructor injection for mandatory dependencies and field injection for circular ones\n")
        result.append("- Refactoring your component design to break the cycle\n")
        result.append("- Using an interface to create looser coupling between components")

        return result.toString()
    }
}