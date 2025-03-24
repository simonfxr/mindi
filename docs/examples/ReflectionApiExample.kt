package de.sfxr.mindi.examples

import de.sfxr.mindi.Context
import de.sfxr.mindi.Plan
import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.reflect.ComponentScanner

/**
 * Example demonstrating the JVM reflection-based API with component scanning
 */
fun reflectionApiExample() {
    // 1. Define components using annotations

    // A repository component
    @Component
    class ProductRepository {
        private val products = mutableMapOf(
            "1" to Product("1", "Laptop", 999.99),
            "2" to Product("2", "Smartphone", 599.99),
            "3" to Product("3", "Headphones", 199.99)
        )

        fun findById(id: String): Product? = products[id]

        fun save(product: Product) {
            products[product.id] = product
        }
    }

    // A service component with constructor injection
    @Component
    class ProductService(private val repository: ProductRepository) {

        @PostConstruct
        fun initialize() {
            println("ProductService initialized")
        }

        fun getProduct(id: String): Product? = repository.findById(id)

        fun createProduct(name: String, price: Double): Product {
            val id = (repository.findAll().size + 1).toString()
            val product = Product(id, name, price)
            repository.save(product)
            return product
        }

        @PreDestroy
        fun cleanup() {
            println("ProductService shutting down")
        }
    }

    // A controller component with field injection
    @Component
    class ProductController {
        @Autowired
        private lateinit var productService: ProductService

        fun getProductDetails(id: String): String {
            val product = productService.getProduct(id)
            return product?.let { "Product: ${it.name}, Price: $${it.price}" }
                ?: "Product not found"
        }

        @EventListener
        fun onProductCreated(event: ProductCreatedEvent) {
            println("New product created: ${event.product.name}")
        }
    }

    // A configuration component with value injection
    @Component
    class ApplicationConfig(
        @Value("\${app.name:Product Manager}")
        val appName: String,

        @Value("\${app.version:1.0}")
        val version: String
    ) {
        @PostConstruct
        fun logConfig() {
            println("Starting $appName v$version")
        }
    }

    // Data classes
    data class Product(val id: String, val name: String, val price: Double)
    data class ProductCreatedEvent(val product: Product)

    // Extension function to find all products
    fun ProductRepository.findAll(): List<Product> = listOf(
        findById("1")!!,
        findById("2")!!,
        findById("3")!!
    )

    // 2. Scan for components
    val components = ComponentScanner.findComponents(listOf("de.sfxr.mindi.examples"))

    // 3. Create and use the context with automatic resource management
    Context.instantiate(components).use { context ->
        // Get the controller
        val controller = context.get<ProductController>()

        // Get product details
        val productDetails = controller.getProductDetails("1")
        println(productDetails)

        // Get the service and create a new product
        val productService = context.get<ProductService>()
        val newProduct = productService.createProduct("Tablet", 399.99)

        // Publish an event
        context.publishEvent(ProductCreatedEvent(newProduct))

        // Check the new product
        println(controller.getProductDetails(newProduct.id))

        // Context will be automatically closed when exiting this block
    }
}