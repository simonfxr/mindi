package de.sfxr.mindi

import de.sfxr.mindi.annotations.Autowired
import de.sfxr.mindi.reflect.Reflector
import de.sfxr.mindi.reflect.reflect
import de.sfxr.mindi.reflect.reflectConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

/**
 * Tests for supporting function types and other polymorphic types as dependencies using reflection
 */
class FunctionTypeReflectionTest {

    // Test classes for function type injection with annotations
    class Product(val name: String, val price: Double)

    @ComponentAnnotation
    class ProductFactory(
        @Autowired
        val createProduct: (String, Double) -> Product,

        @Autowired
        val defaultProductProvider: () -> Product
    )

    @ComponentAnnotation
    class ProductService(
        @Autowired
        val factory: ProductFactory
    ) {
        fun createCustomProduct(name: String, price: Double): Product {
            return factory.createProduct(name, price)
        }

        fun getDefaultProduct(): Product {
            return factory.defaultProductProvider()
        }
    }

    // Implementing using lambda instead of methods because reflectConstructor has issues with function references
    private val productCreator: (String, Double) -> Product = { name, price ->
        Product(name, price)
    }

    fun getProductCreator() = productCreator

    private val defaultProductProvider: () -> Product = { Product("Default", 9.99) }

    fun getDefaultProductProvider() = defaultProductProvider

    @Test
    fun testFunctionTypeInjection() {
        val factoryFunctionComponent = Reflector.Default.reflectConstructor(::getProductCreator)
        val providerFunctionComponent = Reflector.Default.reflectConstructor(::getDefaultProductProvider)
        val factoryComponent = Reflector.Default.reflect<ProductFactory>()
        val serviceComponent = Reflector.Default.reflect<ProductService>()

        // Build plan and create context
        val plan = Plan.build(listOf(
            factoryFunctionComponent,
            providerFunctionComponent,
            factoryComponent,
            serviceComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the service with function type dependencies
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

        context.close()
    }

    // Base and derived classes for covariance testing
    open class Animal(val name: String)
    class Dog(name: String, val breed: String) : Animal(name)
    class Cat(name: String, val color: String) : Animal(name)

    // Service with covariant function types as annotations
    @ComponentAnnotation
    class AnimalService(
        @Autowired
        val animalProvider: () -> Animal,

        @Autowired
        val animalFactory: (String) -> Animal
    )

    // Implementation of covariant function providers
    private val dogProvider: () -> Dog = { Dog("Buddy", "Golden Retriever") }

    fun getDogProvider() = dogProvider

    private val catFactory: (String) -> Cat = { name -> Cat(name, "Orange") }

    fun getCatFactory() = catFactory

    @Test
    fun testCovariantFunctionTypeInjection() {
        // Reflect on the functions without explicitly specifying supertypes
        val dogProviderComponent = Reflector.Default.reflectConstructor(::getDogProvider)
            .withSuperType<_, () -> Animal>()

        val catFactoryComponent = Reflector.Default.reflectConstructor(::getCatFactory)
            .withSuperType<_, (String) -> Animal>()

        // Reflect on the service that requires the function types
        val animalServiceComponent = with(Reflector.Default) {
            reflect<AnimalService>()
        }

        // Build plan and create context
        val plan = Plan.build(listOf(
            dogProviderComponent,
            catFactoryComponent,
            animalServiceComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the service with covariant function types
        val service = context.shared.components
            .firstOrNull { it.klass == AnimalService::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as AnimalService
            }

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

        context.close()
    }

    // Test for contravariant parameter types in function injection
    class Feeder {
        fun feed(animal: Animal) = "${animal.name} has been fed"
        fun feedDog(dog: Dog) = "${dog.name} (${dog.breed}) has been fed"
    }

    @ComponentAnnotation
    class FeedingService(
        @Autowired
        val dogFeeder: (Dog) -> String
    )

    // Implementation of contravariant function
    private val feeder = Feeder()
    private val animalFeeder: (Animal) -> String = feeder::feed

    fun getAnimalFeeder() = animalFeeder

    @Test
    fun testContravariantFunctionTypeInjection() {
        // Reflect on the function without explicitly specifying contravariant relationship
        val feederComponent = Reflector.Default.reflectConstructor(::getAnimalFeeder)
            .withSuperType<_, (Dog) -> String>()

        // Reflect on the service that requires the function type
        val feedingServiceComponent = with(Reflector.Default) {
            reflect<FeedingService>()
        }

        // Build plan and create context
        val plan = Plan.build(listOf(
            feederComponent,
            feedingServiceComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the service with contravariant function types
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

        context.close()
    }

    // Test for both covariance and contravariance with complex function types
    class Transformer<T, R>(val transform: (T) -> R)

    @ComponentAnnotation
    class AnimalTransformer(
        @Autowired
        val dogToStringTransformer: (Dog) -> String,

        @Autowired
        val animalToNameTransformer: (Animal) -> String
    )

    // Implementation of mixed variance functions
    private val dogDescriber: (Dog) -> String = { dog -> "${dog.name} is a ${dog.breed}" }
    fun getDogDescriber() = dogDescriber

    private val animalNamer: (Animal) -> String = { animal -> animal.name }
    fun getAnimalNamer() = animalNamer

    @Test
    fun testMixedVarianceFunctionTypeInjection() {
        // Create components for the functions without explicit supertype declarations
        val dogDescriberComponent = Reflector.Default.reflectConstructor(::getDogDescriber)

        val animalNamerComponent = Reflector.Default.reflectConstructor(::getAnimalNamer)

        // Reflect on the service that requires both function types
        val transformerComponent = with(Reflector.Default) {
            reflect(AnimalTransformer::class)
        }

        // Build plan and create context
        val plan = Plan.build(listOf(
            dogDescriberComponent,
            animalNamerComponent,
            transformerComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the transformer service
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

        context.close()
    }
}