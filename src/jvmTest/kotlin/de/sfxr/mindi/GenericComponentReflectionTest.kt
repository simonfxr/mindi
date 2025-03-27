package de.sfxr.mindi

import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue


class GenericComponentReflectionTest {
    // Base test classes with generic parameters
    open class Parent<T>

    class Child : Parent<String>()

    open class GenericChild<T> : Parent<List<T>>()

    class NestedChild : GenericChild<Int>()


    @Test
    fun testGenericSupertype() {
        val component = Reflector.Default.reflect<Child>()

        // Check that superTypes includes Parent<String>
        val hasParentWithStringParam =
            typeOf<Parent<String>>() in component.superTypes

        assertTrue(hasParentWithStringParam, "Should find Parent<String> in supertypes")
    }

    @Test
    fun testNestedGenericSupertypeWithReflector() {
        val component = Reflector.Default.reflect<NestedChild>()
        assertTrue(typeOf<Parent<List<Int>>>() in component.superTypes,
                "Should find Parent<List<Int>> in supertypes")
    }

    // More complex nested generic type tests
    open class Box<T>
    open class Wrapper<T> : Box<List<T>>()
    open class DoubleWrapper<T> : Wrapper<Set<T>>()
    class TripleWrapper : DoubleWrapper<String>()

    @Test
    fun testComplexNestedGenericTypes() {
        val component = Reflector.Default.reflect<TripleWrapper>()

        // Check intermediate types
        assertTrue(typeOf<Wrapper<Set<String>>>() in component.superTypes,
                "Should find Wrapper<Set<String>> in supertypes")

        // Check fully resolved type
        assertTrue(typeOf<Box<List<Set<String>>>>() in component.superTypes,
                "Should find Box<List<Set<String>>> in supertypes")
    }

    // Classes for testing different variance types

    // Invariant type parameter
    open class Container<T>(val value: T)

    // Covariant type parameter (out)
    open class Producer<out T>(val value: T) {
        fun get(): T = value
    }

    // Contravariant type parameter (in)
    open class Consumer<in T> {
        fun consume(value: T) {}
    }

    // Classes that extend with different variance
    class StringContainer : Container<String>("test")
    class IntProducer : Producer<Int>(42)
    class AnyConsumer : Consumer<Any>()

    // Classes that use star projections in inheritance
    open class GenericProducer<out T>(value: List<T>) : Producer<List<T>>(value)
    // We need a concrete class for the star projection
    class StarProducer : GenericProducer<Any>(listOf())

    open class WildcardBox<T>(value: List<*>) : Container<List<*>>(value)
    class WildcardChild : WildcardBox<String>(listOf(1, "test", 3.14))

    @Test
    fun testInvariantTypeSubstitution() {
        val component = Reflector.Default.reflect<StringContainer>()
        assertTrue(typeOf<Container<String>>() in component.superTypes,
                "Should find Container<String> in supertypes")
    }

    @Test
    fun testCovariantTypeSubstitution() {
        val component = Reflector.Default.reflect<IntProducer>()
        assertTrue(typeOf<Producer<Int>>() in component.superTypes,
                "Should find Producer<Int> in supertypes")
    }

    @Test
    fun testContravariantTypeSubstitution() {
        val component = Reflector.Default.reflect<AnyConsumer>()
        assertTrue(typeOf<Consumer<Any>>() in component.superTypes,
                "Should find Consumer<Any> in supertypes")
    }

    @Test
    fun testStarProjectionSubstitution() {
        val component = Reflector.Default.reflect<StarProducer>()

        // Since we had to use Any instead of star projection in the direct inheritance,
        // we check for the concrete type
        assertTrue(typeOf<GenericProducer<Any>>() in component.superTypes,
                "Should find GenericProducer<Any> in supertypes")

        // The parent type should have the concrete type substitution
        assertTrue(typeOf<Producer<List<Any>>>() in component.superTypes,
                "Should find Producer<List<Any>> in supertypes")
    }

    @Test
    fun testNestedStarProjection() {
        val component = Reflector.Default.reflect<WildcardChild>()

        // Substitution should maintain the star projection in the parent type
        assertTrue(typeOf<Container<List<*>>>() in component.superTypes,
                "Should find Container<List<*>> in supertypes")
    }
}


