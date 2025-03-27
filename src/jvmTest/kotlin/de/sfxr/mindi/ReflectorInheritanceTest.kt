package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflectorInheritanceTest {

    class Priv

    open class Foo {
        @Autowired
        open var baz: Int = 0

        @Autowired
        private var priv: Priv = Priv()
    }

    open class Baz : Foo() {
        @Autowired
        override var baz: Int
            get() = super.baz
            set(value) {
                super.baz = 42
            }

        @Autowired
        private var priv: Priv = Priv()
    }

    @Test
    fun testScanMembersDoesNotDuplicateOverrides() {
        // Reflect on the Baz class which has an overridden field
        val component = Reflector.Default.reflect<Baz>()

        // Verify the fields include one Int and two Priv types
        val intFields = component.fields.filterIsInstance<Dependency.Single>().filter { it.klass == Int::class }
        val privFields = component.fields.filterIsInstance<Dependency.Single>().filter { it.klass == Priv::class }

        // There should be exactly one Int field (baz) despite being overridden
        // This is the key test - we want to ensure that the 'baz' field is only counted once
        // even though it exists in both Foo and Baz classes with an override
        assertEquals(1, intFields.size, "Should have exactly one Int field (non-private fields should not be duplicated when overridden)")

        // There should be exactly two Priv fields (one from each class)
        // This verifies that private fields are correctly tracked separately for each class
        assertEquals(2, privFields.size, "Should have two separate Priv fields (private fields from different classes should be counted separately)")

        // Total number of fields should be 3 (1 Int + 2 Priv)
        assertEquals(3, component.fields.size, "Should have 3 fields total")
    }

    @Test
    fun testInheritanceFieldResolution() {
        // This test ensures that fields from parent classes are correctly scanned
        val component = Reflector.Default.reflect<Baz>()

        // Get the field dependencies
        val fields = component.fields.filterIsInstance<Dependency.Single>()

        // Verify field types
        val fieldTypes = fields.map { it.klass }.toSet()
        assertTrue(Int::class in fieldTypes, "Component should have an Int field")
        assertTrue(Priv::class in fieldTypes, "Component should have Priv fields")

        // Verify that scanning hierarchy works properly
        assertTrue(component.superTypes.isNotEmpty(), "Component should have supertypes")
        assertTrue(component.superTypes.any { it.classifier == Foo::class }, "Foo should be listed as a supertype")
    }
}