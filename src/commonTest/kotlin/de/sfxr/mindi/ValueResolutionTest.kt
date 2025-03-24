package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

class TestResolver : ValueResolver {
    private val values = mapOf(
        "test.key" to "testValue",
        "test.number" to "42",
        "test.boolean" to "true"
    )

    override fun resolve(key: String): Any? = values[key]
}

class ValueResolutionTest {

    private val resolver = TestResolver()

    // Class for testing requireValue
    data class ConfigClass(val stringValue: String, val intValue: Int, val boolValue: Boolean)

    @Test
    fun testParsing() {
        assertEquals("test", resolver.parseValue(TypeProxy<String>(), "test"))
        assertEquals(42, resolver.parseValue(TypeProxy<Int>(), "42"))
        assertEquals(3.14, resolver.parseValue(TypeProxy<Double>(), "3.14"))
        assertEquals(true, resolver.parseValue(TypeProxy<Boolean>(), "true"))
        assertEquals(Duration.parse("PT1H"), resolver.parseValue(TypeProxy<Duration>(), "PT1H"))

        assertFailsWith<NumberFormatException> {
            resolver.parseValue(TypeProxy<Int>(), "not-a-number")
        }
    }

    @Test
    fun testValueResolver() {
        // Test with existing values
        val stringResult = resolver.resolveValue(Dependency.Value<String>(TypeProxy<String>(), "test.key", null))
        assertTrue(stringResult.isSuccess)
        assertEquals("testValue", stringResult.getOrNull())

        val intResult = resolver.resolveValue(Dependency.Value<Int>(TypeProxy<Int>(), "test.number", null))
        assertTrue(intResult.isSuccess)
        assertEquals(42, intResult.getOrNull())

        val boolResult = resolver.resolveValue(Dependency.Value<Boolean>(TypeProxy<Boolean>(), "test.boolean", null))
        assertTrue(boolResult.isSuccess)
        assertEquals(true, boolResult.getOrNull())

        // Test with default values
        val missingResult = resolver.resolveValue(Dependency.Value<String>(TypeProxy<String>(), "missing.key", "defaultValue"))
        assertTrue(missingResult.isSuccess)
        assertEquals("defaultValue", missingResult.getOrNull())

        // Test missing value without default
        val missingNoDefault = resolver.resolveValue(Dependency.Value<String>(TypeProxy<String>(), "missing.key", null))
        assertTrue(missingNoDefault.isFailure)
    }

    @Test
    fun testValueParser() {
        // Test direct value
        val parsed = Dependency.parseValueExpression(TypeProxy<Int>(), "100")
        assertEquals(100, resolver.resolveValue(parsed).getOrThrow())

        // Test placeholder with variable
        val varParsed = Dependency.parseValueExpression(TypeProxy<String>(), "\${test.key}")
        val varResult = resolver.resolveValue(varParsed)
        assertEquals("testValue", varResult.getOrThrow())

        // Test placeholder with default
        val defaultParsed = Dependency.parseValueExpression(TypeProxy<Int>(), "\${missing.key:99}")
        val defaultResult = resolver.resolveValue(defaultParsed)
        assertEquals(99, defaultResult.getOrThrow())
    }

    @Test
    fun testRequireValue() {
        // Create a component with three constructor arguments
        val component = Component { str: String, num: Int, flag: Boolean ->
            ConfigClass(str, num, flag)
        }

        // Use requireValue to set values from resolver
        val configuredComponent = component
            .requireValue(0, "\${test.key}")          // From resolver: "testValue"
            .requireValue(1, "\${test.number}")       // From resolver: 42
            .requireValue(2, "\${missing.key:true}")  // Default: true

        // Check that dependencies were correctly updated to Value type
        val dependencies = configuredComponent.constructorArgs
        assertEquals(3, dependencies.size)

        // Verify all dependencies are now Value dependencies
        dependencies.forEach { dependency ->
            assertTrue(dependency is Dependency.Value<*>, "Dependency should be a Value dependency")
        }

        // Verify the first dependency (string)
        val stringDep = dependencies[0] as Dependency.Value<*>
        assertEquals("test.key", stringDep.variable)
        assertEquals(null, stringDep.default)

        // Verify the second dependency (int)
        val intDep = dependencies[1] as Dependency.Value<*>
        assertEquals("test.number", intDep.variable)
        assertEquals(null, intDep.default)

        // Verify the third dependency (boolean with default)
        val boolDep = dependencies[2] as Dependency.Value<*>
        assertEquals("missing.key", boolDep.variable)
        assertEquals(true, boolDep.default)
    }
}
