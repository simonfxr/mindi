package de.sfxr.examples.webapp

import de.sfxr.mindi.Context
import de.sfxr.mindi.PropertyResolver
import de.sfxr.mindi.reflect.ComponentScanner
import org.apache.logging.log4j.LogManager

/**
 * Main application entry point that uses annotation-based DI
 */
fun main() {
    val log = LogManager.getLogger("Application")
    log.info("Starting webapp example")

    // Enable debug logging for mindi
    System.setProperty("de.sfxr.mindi.debug", "true")

    try {
        // Create property resolver pointing directly to the properties file
        val propertyResolver = PropertyResolver("classpath:application.properties")

        // Log property loading status
        if (!propertyResolver.isLoaded) {
            log.warn("application.properties file not found")
        } else {
            log.info("Loaded properties: ${propertyResolver.getAllProperties().keys.joinToString()}")
        }

        // Scan for components with annotations in this package
        val components = ComponentScanner.findComponents(listOf("de.sfxr.examples.webapp"))

        // Create and use context with automatic resource management
        log.info("Creating application context with property resolver")
        Context.instantiate(components, resolver = propertyResolver).use { context ->
            log.info("Application context created successfully")

            // The server is already started via @PostConstruct
            val server = context.get<NettyServer>()

            log.info("Server is running. Press Ctrl+C to stop.")

            // Add a shutdown hook to detect Ctrl+C
            val mainThread = Thread.currentThread()
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    log.info("Shutdown signal received")
                    mainThread.interrupt()
                },
            )

            // Keep the main thread alive
            try {
                Thread.currentThread().join()
            } catch (e: InterruptedException) {
            }

            log.info("Shutting down application")
            // When this block exits, the context is automatically closed:
            // 1. @PreDestroy methods are called
            // 2. AutoCloseable components (like our NettyServer) are closed
        }
    } catch (e: Exception) {
        log.error("Error during application startup", e)
    }
}
