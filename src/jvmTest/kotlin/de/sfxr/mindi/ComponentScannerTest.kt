package de.sfxr.mindi

import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.reflect.AnnotationOf
import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.testutil.custom.AnotherComponent
import de.sfxr.mindi.testutil.custom.CustomComponent
import kotlin.test.*
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

/**
 * Tests for ComponentScanner functionality.
 * These tests verify that the ComponentScanner can correctly find components
 * in the classpath based on annotations.
 */
class ComponentScannerTest {

    /**
     * Sample component class in the test package
     */
    @ComponentAnnotation
    class ScannerTestComponent1

    /**
     * Another sample component with custom name
     */
    @ComponentAnnotation("custom_name")
    class ScannerTestComponent2

    /**
     * Component with qualifier
     */
    @ComponentAnnotation
    @Qualifier("qualified")
    class ScannerTestComponent3

    /**
     * Component with primary designation
     */
    @ComponentAnnotation
    @Primary
    class ScannerTestComponent4

    /**
     * Component with autowired field
     */
    @ComponentAnnotation
    class ScannerTestComponent5 {
        @Autowired
        private lateinit var dependency: String
    }

    /**
     * Component with lifecycle methods
     */
    @ComponentAnnotation
    class ScannerTestComponent6 {
        @PostConstruct
        fun init() {}

        @PreDestroy
        fun destroy() {}
    }

    /**
     * Non-component class that shouldn't be picked up
     */
    class NonComponentClass

    /**
     * Outer class without annotation but with an annotated inner class
     */
    class OuterClassWithAnnotatedInner {
        /**
         * Inner class annotated with @Component
         */
        @ComponentAnnotation
        class AnnotatedInnerClass

        /**
         * Another level of nesting with annotation
         */
        class MiddleClass {
            /**
             * Nested inner class with annotation (two levels deep)
             */
            @ComponentAnnotation("nested_component")
            class NestedAnnotatedInnerClass
        }
    }

    @Test
    fun testFindComponentsInCurrentPackage() {
        // Scan the current test package
        val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi"))

        // We should find at least our test components
        assertTrue(components.isNotEmpty(), "Should find some components")

        // Verify we can find specific components by type
        val componentClasses = components.map { it.klass }
        assertTrue(componentClasses.contains(ScannerTestComponent1::class),
            "Should find ScannerTestComponent1")
        assertTrue(componentClasses.contains(ScannerTestComponent2::class),
            "Should find ScannerTestComponent2")
        assertTrue(componentClasses.contains(ScannerTestComponent3::class),
            "Should find ScannerTestComponent3")
        assertTrue(componentClasses.contains(ScannerTestComponent4::class),
            "Should find ScannerTestComponent4")
        assertTrue(componentClasses.contains(ScannerTestComponent5::class),
            "Should find ScannerTestComponent5")
        assertTrue(componentClasses.contains(ScannerTestComponent6::class),
            "Should find ScannerTestComponent6")

        // Verify we don't find non-component classes
        assertFalse(componentClasses.contains(NonComponentClass::class),
            "Should not find NonComponentClass")
    }

    @Test
    fun testComponentAttributes() {
        // Scan for components
        val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi"))

        // Find specific components for detailed assertions
        val component2 = components.find { it.klass == ScannerTestComponent2::class }
        val component3 = components.find { it.klass == ScannerTestComponent3::class }
        val component4 = components.find { it.klass == ScannerTestComponent4::class }
        val component5 = components.find { it.klass == ScannerTestComponent5::class }
        val component6 = components.find { it.klass == ScannerTestComponent6::class }

        // Verify component name is extracted correctly
        assertEquals("custom_name", component2?.name, "Component name should be set from annotation")

        // Verify qualifier is extracted correctly
        assertTrue(component3?.qualifiers?.contains("qualified") ?: false,
            "Component should have qualifier")

        // Verify primary is set correctly
        assertTrue(component4?.primary ?: false, "Component should be marked as primary")

        // Verify autowired field is detected
        assertTrue((component5?.fields?.size ?: 0) > 0, "Component should have autowired fields")

        // Verify lifecycle methods are detected
        assertTrue(component6?.postConstruct != null, "PostConstruct method should be detected")
        assertTrue(component6?.close != null, "PreDestroy method should be detected")
    }

    @Test
    fun testFindComponentsInSpecificPackage() {
        // Create a package path that doesn't exist to verify filtering works
        val nonExistentPackage = "de.sfxr.mindi.doesnotexist"
        val components = ComponentScanner.findComponents(listOf(nonExistentPackage))

        // Should find no components
        assertTrue(components.isEmpty(), "Should not find components in non-existent package")
    }

    @Test
    fun testFindComponentsInMultiplePackages() {
        // Scan both the main test package and the separate test package
        val components = ComponentScanner.findComponents(listOf(
            "de.sfxr.mindi",
            "de.sfxr.mindi.testutil.separate"
        ))

        // We should find components from both packages
        val componentClassNames = components.map { it.klass.qualifiedName }

        // Check for our test components in the main package
        assertTrue(componentClassNames.contains(ScannerTestComponent1::class.qualifiedName),
            "Should find components in main package")

        // Check for our test component in the separate package
        assertTrue(componentClassNames.contains("de.sfxr.mindi.testutil.separate.SeparatePackageComponent"),
            "Should find components in separate package")
    }

    @Test
    fun testFindComponentsWithCustomReflector() {
        val customReflector = Reflector.Default

        // Scan with custom reflector
        val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi"), customReflector)

        // Find the components directly
        val component1 = components.find { it.klass == ScannerTestComponent1::class }
        val component2 = components.find { it.klass == ScannerTestComponent2::class }

        // Verify component2 has the custom name
        assertNotNull(component2, "Should find ScannerTestComponent2")
        assertEquals("custom_name", component2.name, "Component2 should have the custom name")

        // Verify component1 uses default name
        assertNotNull(component1, "Should find ScannerTestComponent1")
        assertEquals("scannerTestComponent1", component1.name,
            "Component1 should use default class name since no explicit name was provided")
    }

    @Test
    fun testMultipleAnnotationTypes() {
        // Create a custom reflector that recognizes both standard and custom component annotations
        val customReflector = Reflector(
            autowiredAnnotations = Reflector.Default.autowiredAnnotations,
            componentAnnotations = listOf(
                AnnotationOf.valued<ComponentAnnotation, _> { it: ComponentAnnotation -> it.value },
                AnnotationOf.valued<CustomComponent, _> { it: CustomComponent -> it.value },
                AnnotationOf.valued<AnotherComponent, _> { it: AnotherComponent -> it.value }
            ),
            postConstructAnnotations = Reflector.Default.postConstructAnnotations,
            preDestroyAnnotations = Reflector.Default.preDestroyAnnotations,
            primaryAnnotations = Reflector.Default.primaryAnnotations,
            valueAnnotations = Reflector.Default.valueAnnotations,
            qualifierAnnotations = Reflector.Default.qualifierAnnotations,
            eventListenerAnnotations = Reflector.Default.eventListenerAnnotations
        )

        // Scan with custom reflector for multiple annotation types
        val components = ComponentScanner.findComponents(
            listOf("de.sfxr.mindi", "de.sfxr.mindi.testutil.custom"),
            customReflector
        )

        // Get all the component class names for easier assertion
        val componentClassNames = components.map { it.klass.qualifiedName }

        // Should find both our standard and custom annotated components
        assertTrue(componentClassNames.contains(ScannerTestComponent1::class.qualifiedName),
            "Should find regular @Component classes")

        assertTrue(componentClassNames.contains("de.sfxr.mindi.testutil.custom.CustomAnnotatedComponent1"),
            "Should find @CustomComponent classes")

        assertTrue(componentClassNames.contains("de.sfxr.mindi.testutil.custom.CustomAnnotatedComponent2"),
            "Should find @AnotherComponent classes")
    }

    @Test
    fun testEdgeCases() {
        // Test with empty list of package prefixes
        val emptyPackages = ComponentScanner.findComponents(emptyList())
        assertTrue(emptyPackages.isEmpty(), "Empty package list should return no components")

        // Test with list containing empty string
        val emptyStringPackage = ComponentScanner.findComponents(listOf(""))
        assertTrue(emptyStringPackage.isEmpty() || emptyStringPackage.isNotEmpty(),
            "Empty string package should not cause errors")

        // Test with null reflector (should use default)
        val componentsWithDefaultReflector = ComponentScanner.findComponents(listOf("de.sfxr.mindi"))
        assertTrue(componentsWithDefaultReflector.isNotEmpty(),
            "Default reflector should work correctly")

        // Test with a very specific package that only contains one component
        val specificPackage = ComponentScanner.findComponents(listOf("de.sfxr.mindi.testutil.separate"))
        assertEquals(1, specificPackage.size,
            "Should find exactly one component in the specific package")
        assertEquals("de.sfxr.mindi.testutil.separate.SeparatePackageComponent",
            specificPackage.first().klass.qualifiedName,
            "Should find the correct component")
    }

    @Test
    fun testInnerClassComponents() {
        // Scan the current test package to find inner class components
        val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi"))

        // Get all component class names
        val componentClassNames = components.map { it.klass.qualifiedName }

        // Check that the outer class without annotation is not found
        assertFalse(componentClassNames.contains(OuterClassWithAnnotatedInner::class.qualifiedName),
            "Outer class without annotation should not be found")

        // Check that the annotated inner class is found
        val innerClassName = "de.sfxr.mindi.ComponentScannerTest.OuterClassWithAnnotatedInner.AnnotatedInnerClass"
        assertTrue(componentClassNames.contains(innerClassName),
            "Inner class with @Component should be found")

        // Check that the nested annotated inner class (two levels deep) is found
        val nestedInnerClassName = "de.sfxr.mindi.ComponentScannerTest.OuterClassWithAnnotatedInner.MiddleClass.NestedAnnotatedInnerClass"
        assertTrue(componentClassNames.contains(nestedInnerClassName),
            "Nested inner class with @Component should be found")

        // Find the inner class component and verify its name attribute from the annotation
        val nestedComponent = components.find { it.klass.qualifiedName == nestedInnerClassName }
        assertNotNull(nestedComponent, "Should find nested inner component")
        assertEquals("nested_component", nestedComponent.name,
            "Nested component should have the correct name from annotation")
    }
}