package de.sfxr.mindi

import de.sfxr.mindi.annotations.Qualifier
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

// Define custom qualifier annotations
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Qualifier
annotation class Dog

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Qualifier
annotation class Cat

class CustomQualifierTest {

    // Define a common interface
    interface Animal {
        fun makeSound(): String
    }

    // Implement with different custom qualifiers
    @ComponentAnnotation
    @Dog
    class DogImpl : Animal {
        override fun makeSound(): String = "Woof!"
    }

    @ComponentAnnotation
    @Cat
    class CatImpl : Animal {
        override fun makeSound(): String = "Meow!"
    }

    // Class with constructor injection using custom qualifier
    class PetOwner(@Dog val pet: Animal) {
        fun getPetSound(): String = pet.makeSound()
    }

    // For simplicity, we'll use constructor injection instead of field injection
    class PetShelter(@Cat val cat: Animal)

    @Test
    fun testCustomQualifierOnConstructorInjection() {
        // Create components using reflection with custom qualifiers
        val dogComponent = Reflector.Default.reflect(DogImpl::class)
        val catComponent = Reflector.Default.reflect(CatImpl::class)

        // Verify components have the right qualifiers
        assertTrue(dogComponent.qualifiers.any { Dog::class == (it as? Annotation)?.annotationClass })
        assertTrue(catComponent.qualifiers.any { Cat::class == (it as? Annotation)?.annotationClass })

        // Create pet owner component with constructor injection using @Dog qualifier
        val petOwnerComponent = Reflector.Default.reflect(PetOwner::class)

        // Create plan and context
        val plan = Plan.build(listOf(dogComponent, catComponent, petOwnerComponent))
        Context.instantiate(plan).use { context ->
            val petOwner = context.instances.filterIsInstance<PetOwner>().first()

            // The pet should be a DogImpl, even though there are two Animal implementations
            assertIs<DogImpl>(petOwner.pet)
            assertEquals("Woof!", petOwner.getPetSound())
        }
    }

    @Test
    fun testCustomQualifierOnSecondConstructorParam() {
        // Create components
        val dogComponent = Reflector.Default.reflect(DogImpl::class)
        val catComponent = Reflector.Default.reflect(CatImpl::class)
        val shelterComponent = Reflector.Default.reflect(PetShelter::class)

        // Create plan and context
        val plan = Plan.build(listOf(dogComponent, catComponent, shelterComponent))
        Context.instantiate(plan).use { context ->
            val shelter = context.instances.filterIsInstance<PetShelter>().first()

            // The cat parameter should be a CatImpl due to @Cat qualifier
            assertIs<CatImpl>(shelter.cat)
            assertEquals("Meow!", shelter.cat.makeSound())
        }
    }
}