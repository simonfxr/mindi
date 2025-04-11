package de.sfxr.mindi

import de.sfxr.mindi.annotations.Bean
import de.sfxr.mindi.annotations.Primary
import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.classType
import de.sfxr.mindi.reflect.reflectFactory
import kotlin.reflect.full.starProjectedType
import kotlin.test.*

class ReflectorFactoryTest {

    // Sample factory classes with various bean methods
    class SimpleFactory {
        @Bean
        fun createString(): String = "test-string"

        @Bean
        fun createInt(): Int = 42
    }

    class FactoryWithQualifiers {
        @Bean
        @Qualifier("primary")
        @Primary
        fun primaryString(): String = "primary"

        @Bean
        @Qualifier("secondary")
        fun secondaryString(): String = "secondary"
    }

    class GenericClass<T>(val value: T)

    // Class without accessible no-arg constructor
    class NoDefaultConstructor(val value: String)

    // Class with multiple constructors but one public no-arg
    class MultipleConstructors {
        constructor()
        constructor(value: String)
    }

    @Test
    fun testClassTypeFunction() {
        // Test with simple class
        val stringType = classType(String::class)
        assertEquals(String::class, stringType.klass)
        assertEquals(String::class.starProjectedType, stringType.type)

        // Test with generic class error
        assertFailsWith<IllegalArgumentException> {
            classType(GenericClass::class)
        }
    }

    @Test
    fun testReflectFactoryWithTypeProxy() {
        // Test reflecting on a factory class using TypeProxy
        val stringTypeProxy = TypeProxy<String>(String::class.starProjectedType)
        String()
        // Should work as String does have a default constructor
        val stringComponents = Reflector.Default.reflectFactory(stringTypeProxy)
        assertEquals(listOf(), stringComponents)

        // Test with a class that has a public no-arg constructor
        val simpleFactoryType = TypeProxy<SimpleFactory>(SimpleFactory::class.starProjectedType)
        val components = Reflector.Default.reflectFactory(simpleFactoryType)
        
        assertEquals(2, components.size, "Should find 2 bean components")
        
        // Check component types
        assertTrue(components.any { it.type.classifier == String::class }, "Should have String component")
        assertTrue(components.any { it.type.classifier == Int::class }, "Should have Int component")
    }

    @Test
    fun testReflectFactoryWithReifiedType() {
        val cons = SimpleFactory::class.constructors
        check(cons.isNotEmpty())
        // Test the reified type helper method
        val components = Reflector.Default.reflectFactory<SimpleFactory>()
        
        assertEquals(2, components.size, "Should find 2 bean components")
        
        // Check if names match method names
        val names = components.map { it.name }
        assertTrue("createString" in names, "Should have createString component")
        assertTrue("createInt" in names, "Should have createInt component")
    }

    @Test
    fun testReflectFactoryWithKClass() {
        // Test with KClass parameter
        val components = Reflector.Default.reflectFactory(FactoryWithQualifiers::class)
        
        assertEquals(2, components.size, "Should find 2 bean components")
        
        // Check qualifiers and primary annotation 
        val primaryComponent = components.find { it.qualifiers.contains("primary") }
        assertNotNull(primaryComponent, "Should have component with 'primary' qualifier")
        assertTrue(primaryComponent.primary, "Component with 'primary' qualifier should be marked as primary")
        
        val secondaryComponent = components.find { it.qualifiers.contains("secondary") }
        assertNotNull(secondaryComponent, "Should have component with 'secondary' qualifier")
        assertFalse(secondaryComponent.primary, "Component with 'secondary' qualifier should not be primary")
    }

    @Test
    fun testReflectFactoryNoArgConstructorErrors() {
        // Should fail when class has no accessible no-arg constructor
        assertFailsWith<IllegalArgumentException> {
            Reflector.Default.reflectFactory(NoDefaultConstructor::class)
        }
        
        // Should work with class that has multiple constructors including a no-arg one
        val components = Reflector.Default.reflectFactory(MultipleConstructors::class)
        // We just want to make sure it doesn't throw an exception
        assertTrue(components.isEmpty(), "Should return an empty component list as there are no @Bean methods")
    }

    @Test
    fun testReflectFactoryContextCreation() {
        // Get components from the factory
        val components = Reflector.Default.reflectFactory<FactoryWithQualifiers>()
        
        // Create a context from these components
        Context.instantiate(components).use { ctx ->
            // Test getting the primary String
            val primaryString = ctx.get<String>()
            assertEquals("primary", primaryString, "Primary String should have correct value")
            
            // Test getting qualified String
            val secondaryString = ctx.get<String>("secondary")
            assertEquals("secondary", secondaryString, "Secondary String should have correct value")
            
            // Test getAll for String
            val allStrings = ctx.getAll<String>()
            assertEquals(2, allStrings.size, "Should have 2 String instances")
        }
    }
}