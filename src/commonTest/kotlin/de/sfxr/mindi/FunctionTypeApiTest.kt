package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for supporting function types and other polymorphic types as dependencies
 * using the pure Kotlin API.
 *
 * This is the multiplatform equivalent of the JVM-specific FunctionTypeReflectionTest.
 */
class FunctionTypeApiTest {

    // Test classes for function type injection
    class Product(val name: String, val price: Double)

    class ProductFactory(
        val createProduct: (String, Double) -> Product,
        val defaultProductProvider: () -> Product
    )

    class ProductService(
        val factory: ProductFactory
    ) {
        fun createCustomProduct(name: String, price: Double): Product {
            return factory.createProduct(name, price)
        }

        fun getDefaultProduct(): Product {
            return factory.defaultProductProvider()
        }
    }

    // Implementing using lambda instead of methods
    private val productCreator: (String, Double) -> Product = { name, price ->
        Product(name, price)
    }

    private val defaultProductProvider: () -> Product = { Product("Default", 9.99) }

    @Test
    fun testFunctionTypeInjection() {
        // Create components for the functions with explicit empty argument lists
        val factoryFunctionComponent = Component { -> productCreator }
            .named("productCreator")

        val providerFunctionComponent = Component { -> defaultProductProvider }
            .named("defaultProductProvider")

        // Create factory component with constructor injection of function components
        val factoryComponent = Component { creator: (String, Double) -> Product, provider: () -> Product ->
            ProductFactory(creator, provider)
        }
        .named("productFactory")

        // Create service component with constructor injection of factory
        val serviceComponent = Component { factory: ProductFactory ->
            ProductService(factory)
        }
        .named("productService")

        // Build plan and create context
        val plan = Plan.build(listOf(
            factoryFunctionComponent,
            providerFunctionComponent,
            factoryComponent,
            serviceComponent
        ))

        val context = Context.instantiate(plan)

        try {
            // Get the service instance
            val service = context.shared.components
                .firstOrNull { it.klass == ProductService::class }
                ?.let { component ->
                    val index = context.shared.components.indexOf(component)
                    context.instances[index] as ProductService
                }

            assertNotNull(service)

            // Test the function dependencies
            val customProduct = service.createCustomProduct("Custom", 19.99)
            assertEquals("Custom", customProduct.name)
            assertEquals(19.99, customProduct.price)

            val defaultProduct = service.getDefaultProduct()
            assertEquals("Default", defaultProduct.name)
            assertEquals(9.99, defaultProduct.price)
        } finally {
            context.close()
        }
    }

    // Base and derived classes for covariance testing
    open class Animal(val name: String)
    class Dog(name: String, val breed: String) : Animal(name)
    class Cat(name: String, val color: String) : Animal(name)

    // Service with covariant function types
    class AnimalService(
        val animalProvider: () -> Animal,
        val animalFactory: (String) -> Animal
    )

    // Implementation of covariant function providers
    private val dogProvider: () -> Dog = { Dog("Buddy", "Golden Retriever") }
    private val catFactory: (String) -> Cat = { name -> Cat(name, "Orange") }

    @Test
    fun testCovariantFunctionTypeInjection() {
        // Create components for the functions with superType specification
        val dogProviderComponent = Component { -> dogProvider }
            .withSuperType<_, () -> Animal>()
            .named("dogProvider")

        val catFactoryComponent = Component { -> catFactory }
            .withSuperType<_, (String) -> Animal>()
            .named("catFactory")

        // Create service component with constructor injection
        val animalServiceComponent = Component(::AnimalService)
            .named("animalService")

        // Build plan and create context
        val plan = Plan.build(listOf(
            dogProviderComponent,
            catFactoryComponent,
            animalServiceComponent
        ))

        val context = Context.instantiate(plan)

        try {
            // Get the service instance using the new API
            val service = context.get<AnimalService>()

            assertNotNull(service)

            // Test that the covariant functions work correctly
            val animal1 = service.animalProvider()
            assertTrue(animal1 is Dog)
            assertEquals("Buddy", animal1.name)
            assertEquals("Golden Retriever", animal1.breed)

            val animal2 = service.animalFactory("Whiskers")
            assertTrue(animal2 is Cat)
            assertEquals("Whiskers", animal2.name)
            assertEquals("Orange", animal2.color)
        } finally {
            context.close()
        }
    }

    // Test for contravariant parameter types in function injection
    class Feeder {
        fun feed(animal: Animal) = "${animal.name} has been fed"
        fun feedDog(dog: Dog) = "${dog.name} (${dog.breed}) has been fed"
    }

    class FeedingService(
        val dogFeeder: (Dog) -> String
    )

    // Implementation of contravariant function
    private val feeder = Feeder()
    private val animalFeeder: (Animal) -> String = feeder::feed

    @Test
    fun testContravariantFunctionTypeInjection() {
        // Create a component for the contravariant function
        val feederComponent = Component { -> animalFeeder }
            .withSuperType<_, (Dog) -> String>()
            .named("animalFeeder")

        // Create service component with constructor injection
        val feedingServiceComponent = Component { feeder: (Dog) -> String ->
            FeedingService(feeder)
        }
        .named("feedingService")

        // Build plan and create context
        val plan = Plan.build(listOf(
            feederComponent,
            feedingServiceComponent
        ))

        val context = Context.instantiate(plan)

        try {
            // Get the service instance
            val service = context.shared.components
                .firstOrNull { it.klass == FeedingService::class }
                ?.let { component ->
                    val index = context.shared.components.indexOf(component)
                    context.instances[index] as FeedingService
                }

            assertNotNull(service)

            // Test the function with a specific Dog instance
            val result = service.dogFeeder(Dog("Rex", "German Shepherd"))
            assertEquals("Rex has been fed", result)
        } finally {
            context.close()
        }
    }

    // Test for both covariance and contravariance with complex function types
    class Transformer<T, R>(val transform: (T) -> R)

    class AnimalTransformer(
        val dogToStringTransformer: (Dog) -> String,
        val animalToNameTransformer: (Animal) -> String
    )

    // Implementation of mixed variance functions
    private val dogDescriber: (Dog) -> String = { dog -> "${dog.name} is a ${dog.breed}" }
    private val animalNamer: (Animal) -> String = { animal -> animal.name }

    @Test
    fun testMixedVarianceFunctionTypeInjection() {
        // Create components for the functions
        val dogDescriberComponent = Component { -> dogDescriber }
            .named("dogDescriber")

        val animalNamerComponent = Component { -> animalNamer }
            .named("animalNamer")

        // Create service component with constructor injection
        val transformerComponent = Component { dogToString: (Dog) -> String, animalToName: (Animal) -> String ->
            AnimalTransformer(dogToString, animalToName)
        }
        .named("animalTransformer")

        // Build plan and create context
        val plan = Plan.build(listOf(
            dogDescriberComponent,
            animalNamerComponent,
            transformerComponent
        ))

        val context = Context.instantiate(plan)

        try {
            // Get the transformer instance
            val transformer = context.shared.components
                .firstOrNull { it.klass == AnimalTransformer::class }
                ?.let { component ->
                    val index = context.shared.components.indexOf(component)
                    context.instances[index] as AnimalTransformer
                }

            assertNotNull(transformer)

            // Test both function types
            val dog = Dog("Max", "Labrador")
            val description = transformer.dogToStringTransformer(dog)
            assertEquals("Max is a Labrador", description)

            val name = transformer.animalToNameTransformer(dog)
            assertEquals("Max", name)
        } finally {
            context.close()
        }
    }
}