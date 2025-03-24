package de.sfxr.mindi

import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.testutil.nested.NestedPackageComponent
import de.sfxr.mindi.testutil.nested.deep.DeepNestedComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests recursive package scanning and custom ClassLoader in ComponentScanner.
 */
class ComponentScannerRecursiveTest {

    @Test
    fun testRecursivePackageScanning() {
        // Scan with just the top-level package and verify we find both the nested components
        val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi.testutil"))

        // Get all component class names
        val componentClassNames = components.map { it.klass.qualifiedName }

        // Verify we find the component in the first-level nested package
        assertTrue(componentClassNames.contains(NestedPackageComponent::class.qualifiedName),
            "Should find component in first-level nested package")

        // Verify we find the component in the deeply nested package
        assertTrue(componentClassNames.contains(DeepNestedComponent::class.qualifiedName),
            "Should find component in deeply nested package")
    }

    @Test
    fun testOverlappingPackagePrefixes() {
        // Scan with overlapping package prefixes
        val components = ComponentScanner.findComponents(listOf(
            "de.sfxr.mindi.testutil.nested",  // More specific prefix
            "de.sfxr.mindi.testutil"          // More general prefix that includes the specific one
        ))

        // Get component classes
        val nestedComponent = components.find { it.klass == NestedPackageComponent::class }
        val deepComponent = components.find { it.klass == DeepNestedComponent::class }

        // Verify both components are found
        assertTrue(nestedComponent != null, "Should find NestedPackageComponent")
        assertTrue(deepComponent != null, "Should find DeepNestedComponent")

        // Count how many times each class appears (should be once, not duplicated)
        val nestedComponentCount = components.count { it.klass == NestedPackageComponent::class }
        val deepComponentCount = components.count { it.klass == DeepNestedComponent::class }

        // Verify no duplicates
        assertEquals(1, nestedComponentCount, "NestedPackageComponent should only appear once")
        assertEquals(1, deepComponentCount, "DeepNestedComponent should only appear once")
    }

    @Test
    fun testCustomClassLoader() {
        // Get the current class loader
        val currentClassLoader = Thread.currentThread().contextClassLoader

        // Just verify that we can pass a custom class loader without errors
        // In a real application, a custom URLClassLoader would be constructed with specific URLs
        val components = ComponentScanner.findComponents(
            packagePrefixes = listOf("de.sfxr.mindi.testutil.nested"),
            classLoader = currentClassLoader  // Just use the current class loader directly
        )

        // Verify we can find components
        val componentClassNames = components.map { it.klass.qualifiedName }

        // We should find our test components
        assertTrue(componentClassNames.contains(NestedPackageComponent::class.qualifiedName) ||
                   componentClassNames.contains(DeepNestedComponent::class.qualifiedName),
            "Should find at least one component using the specified class loader")
    }
}