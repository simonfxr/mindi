package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for supporting function types and other polymorphic types as dependencies
 */
class FunctionTypeTest {

    // Test classes for function type injection
    class Product(val name: String, val price: Double)

    // Factory function type
    class ProductFactory(
        // Function that creates a Product
        val createProduct: (String, Double) -> Product,
        // Simple factory function with no parameters
        val defaultProductProvider: () -> Product
    )

    // Service that uses the factory
    class ProductService(val factory: ProductFactory) {
        fun createCustomProduct(name: String, price: Double): Product {
            return factory.createProduct(name, price)
        }

        fun getDefaultProduct(): Product {
            return factory.defaultProductProvider()
        }
    }

    // Implementation of the factory functions
    val productCreator: (String, Double) -> Product = { name, price ->
        Product(name, price)
    }

    val defaultProductProvider: () -> Product = {
        Product("Default", 9.99)
    }

    @Test
    fun testFunctionTypeInjection() {
        // Create components with the new API style
        val factoryFunctionComponent = Component { -> productCreator }
            .named("productCreator")

        val providerFunctionComponent = Component { -> defaultProductProvider }
            .named("defaultProductProvider")

        // Using the new API with requireQualified to set qualifiers on constructor arguments
        val factoryComponent = Component(::ProductFactory)
        .requireQualified(0, "productCreator") // Sets qualifier for first argument
        .requireQualified(1, "defaultProductProvider") // Sets qualifier for second argument
        .named("productFactory")

        val serviceComponent = Component(::ProductService)
            .named("productService")

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

    // Test for generic types
    class Repository<T>(val items: List<T>)

    class StringRepository(val repository: Repository<String>)

    @Test
    fun testGenericTypeInjection() {
        // This component provides a List<String>
        val stringsComponent = Component { -> listOf("one", "two", "three") }
            .named("stringList")

        // This component depends on a List<String> - using constructing to specify the qualified dependency
        val repositoryComponent = Component { l: List<String> -> Repository(l) }
            .requireQualified(0, "stringList")
            .named("stringRepository")

        // This component depends on Repository<String> with qualified dependency
        val stringRepoComponent = Component { repo: Repository<String> -> StringRepository(repo) }
            .requireQualified(0, "stringRepository")
            .named("typedRepository")

        // Build plan and create context
        val plan = Plan.build(listOf(
            stringsComponent,
            repositoryComponent,
            stringRepoComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the generic type dependencies
        val typedRepo = context.shared.components
            .firstOrNull { it.klass == StringRepository::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as StringRepository
            }

        assertNotNull(typedRepo)

        // Test the repository contents
        val items = typedRepo.repository.items
        assertEquals(3, items.size)
        assertEquals("one", items[0])
        assertEquals("two", items[1])
        assertEquals("three", items[2])

        context.close()
    }

    // Base and derived classes for covariance testing
    open class Animal(val name: String)
    class Dog(name: String, val breed: String) : Animal(name)
    class Cat(name: String, val color: String) : Animal(name)

    // Service that consumes functions with covariant return types
    class AnimalService(
        // This function accepts a more specific return type (Dog) where Animal is expected
        val animalProvider: () -> Animal,
        // This function accepts a more specific return type (Cat) in a function with parameters
        val animalFactory: (String) -> Animal
    )

    @Test
    fun testCovariantFunctionTypeInjection() {
        // Implement providers with more specific return types
        val dogProvider: () -> Dog = {
            Dog("Buddy", "Golden Retriever")
        }

        val catFactory: (String) -> Cat = { name ->
            Cat(name, "Orange")
        }

        // Create components for the covariant functions
        val dogProviderComponent = Component { -> dogProvider }
            .named("dogProvider")
            .withSuperType<_, () -> Animal>()

        val catFactoryComponent = Component { -> catFactory }
            .named("catFactory")
            .withSuperType<_, (String) -> Animal>()

        // Create a service that uses the animal providers
        // Using new API with requireQualified to set qualifiers on constructor arguments
        val animalServiceComponent = Component(::AnimalService)
        .named("animalService")

        // Build plan and create context
        val plan = Plan.build(listOf(
            dogProviderComponent,
            catFactoryComponent,
            animalServiceComponent
        ))

        val context = Context.instantiate(plan)

        // Test that the service received the covariant function types
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

    class FeedingService(
        // This could be injected with a function that accepts a more general type
        val dogFeeder: (Dog) -> String
    )

    @Test
    fun testContravariantFunctionTypeInjection() {
        // Create a function that accepts the more general Animal type
        val animalFeeder: (Animal) -> String = { animal ->
            "Feeding ${animal.name}"
        }

        // Create an implementation using a method reference
        val feeder = Feeder()
        val feederMethod: (Animal) -> String = feeder::feed

        // Create components
        val feederComponent = Component { -> feederMethod }
            .named("animalFeeder")
            .withSuperType<_, (Dog) -> String>()

        val feedingServiceComponent = Component(::FeedingService)
            .named("feedingService")

        // Build plan and create context
        val plan = Plan.build(listOf(
            feederComponent,
            feedingServiceComponent
        ))

        val context = Context.instantiate(plan)

        // Test using the contravariant function type with new API
        val service = context.get<FeedingService>()

        assertNotNull(service)

        // Test the function with a specific Dog
        val result = service.dogFeeder(Dog("Rex", "German Shepherd"))
        // The feeder method returns the string from Feeder::feed
        assertEquals("Rex has been fed", result)

        context.close()
    }

    // Test service with default parameter values for function injection
    class DataProcessor(
        // The same function type signature but with a default value parameter
        val intProcessor: (Int) -> String = { it.toString() },
        val stringProcessor: (String) -> String = { "Default: $it" }
    )

    class NumberService(
        val processor: (Int) -> String
    )

    class TextService(
        val processor: (String) -> String
    )

    @Test
    fun testDefaultParametersFunctionTypeInjection() {
        // Create a function that transforms integers
        val intToHex: (Int) -> String = { i -> "0x${i.toString(16).uppercase()}" }

        // Create a function that transforms strings
        val stringCapitalizer: (String) -> String = { s -> s.uppercase() }

        // Create components for the functions
        val intProcessorComponent = Component { -> intToHex }
            .named("intProcessor")

        val stringProcessorComponent = Component { -> stringCapitalizer }
            .named("stringProcessor")

        // Create a service that will use our specific function
        val numberServiceComponent = Component { processor: (Int) -> String -> NumberService(processor) }
            .requireQualified(0, "intProcessor")
            .named("numberService")

        // Create a service that will use our specific function
        val textServiceComponent = Component { processor: (String) -> String -> TextService(processor) }
            .requireQualified(0, "stringProcessor")
            .named("textService")

        // Create a data processor with injectable function dependencies
        // This should use the specifically injected functions, not the default parameter values
        val dataProcessorComponent = Component { intProc: (Int) -> String, stringProc: (String) -> String ->
            DataProcessor(intProc, stringProc)
        }
        .requireQualified(0, "intProcessor")
        .requireQualified(1, "stringProcessor")
        .named("dataProcessor")

        // Create a data processor with DEFAULT function dependencies
        // This should use the default parameter values, not any injected functions
        val defaultDataProcessorComponent = Component { -> DataProcessor() } // No args - uses defaults
            .named("defaultDataProcessor")

        // Build plan and create context
        val plan = Plan.build(listOf(
            intProcessorComponent,
            stringProcessorComponent,
            numberServiceComponent,
            textServiceComponent,
            dataProcessorComponent,
            defaultDataProcessorComponent
        ))

        val context = Context.instantiate(plan)

        // Get the services
        val numberService = context.shared.components
            .firstOrNull { it.klass == NumberService::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as NumberService
            }

        val textService = context.shared.components
            .firstOrNull { it.klass == TextService::class }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as TextService
            }

        // Get the data processors
        val dataProcessor = context.shared.components
            .firstOrNull { it.klass == DataProcessor::class && it.isQualifiedBy("dataProcessor") }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as DataProcessor
            }

        val defaultProcessor = context.shared.components
            .firstOrNull { it.klass == DataProcessor::class && it.isQualifiedBy("defaultDataProcessor") }
            ?.let { component ->
                val index = context.shared.components.indexOf(component)
                context.instances[index] as DataProcessor
            }

        // Test that all services were created
        assertNotNull(numberService)
        assertNotNull(textService)
        assertNotNull(dataProcessor)
        assertNotNull(defaultProcessor)

        // Test that the NumberService got the correct function
        val numberResult = numberService.processor(255)
        assertEquals("0xFF", numberResult)

        // Test that the TextService got the correct function
        val textResult = textService.processor("hello")
        assertEquals("HELLO", textResult)

        // Test the data processor with injected function dependencies
        val injectedIntResult = dataProcessor.intProcessor(255)
        val injectedStringResult = dataProcessor.stringProcessor("hello")
        assertEquals("0xFF", injectedIntResult)
        assertEquals("HELLO", injectedStringResult)

        // Test the data processor with default function dependencies
        val defaultIntResult = defaultProcessor.intProcessor(255)
        val defaultStringResult = defaultProcessor.stringProcessor("hello")
        assertEquals("255", defaultIntResult)
        assertEquals("Default: hello", defaultStringResult)

        // Verify the injected processor is not the default
        assertTrue(defaultProcessor.intProcessor !== dataProcessor.intProcessor)
        assertTrue(defaultProcessor.stringProcessor !== dataProcessor.stringProcessor)

        context.close()
    }
}