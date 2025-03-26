package de.sfxr.mindi

import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

/**
 * A ValueResolver that reads values from Java Properties files.
 *
 * This resolver supports loading properties from:
 * - Classpath resources (prefixed with "classpath:")
 * - File system paths (no prefix or "file:")
 *
 * @param propertiesPath The path to the properties file. Can be a classpath resource
 *                      (e.g., "classpath:application.properties") or a file system path
 *                      (e.g., "config/application.properties" or "file:/etc/app/config.properties")
 */
class PropertyResolver(private val propertiesPath: String) : ValueResolver {
    private val properties: Properties? = loadProperties()

    /**
     * Indicates if properties were successfully loaded
     */
    val isLoaded: Boolean get() = properties != null

    /**
     * Loads properties from the specified path.
     * This is called automatically in the constructor.
     *
     * @return Properties object if loading was successful, null otherwise
     */
    private fun loadProperties(): Properties? {
        val inputStream = when {
            propertiesPath.startsWith("classpath:") -> {
                val resource = propertiesPath.substring("classpath:".length)
                PropertyResolver::class.java.classLoader.getResourceAsStream(resource)
            }
            propertiesPath.startsWith("file:") -> {
                val filePath = propertiesPath.substring("file:".length)
                FileInputStream(filePath)
            }
            else -> {
                // Assume it's a file path
                if (Files.exists(Paths.get(propertiesPath))) {
                    FileInputStream(propertiesPath)
                } else {
                    // Try as a classpath resource as fallback
                    PropertyResolver::class.java.classLoader.getResourceAsStream(propertiesPath)
                }
            }
        }

        return inputStream?.use {
            val props = Properties()
            props.load(it)
            props
        }
    }

    /**
     * Resolves the value for the given key from the loaded properties.
     *
     * @param key The key to resolve
     * @return The property value or null if not found or properties failed to load
     */
    override fun resolve(key: String): String? = properties?.getProperty(key)

    /**
     * Gets all the loaded properties
     *
     * @return A read-only map of all properties, or empty map if properties failed to load
     */
    fun getAllProperties(): Map<String, String> =
        properties?.entries?.associate { (it.key as String) to (it.value as String) } ?: emptyMap()
}