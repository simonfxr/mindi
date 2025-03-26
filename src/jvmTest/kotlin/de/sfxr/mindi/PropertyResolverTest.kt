package de.sfxr.mindi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class PropertyResolverTest {

    @Test
    fun `should load properties from classpath`() {
        // Test with classpath resource
        val resolver = PropertyResolver("classpath:test-properties.properties")
        assertTrue(resolver.isLoaded)
        assertEquals("value1", resolver.resolve("test.property1"))
        assertEquals("value2", resolver.resolve("test.property2"))
    }

    @Test
    fun `should handle missing classpath resource`() {
        // Test with non-existent resource
        val resolver = PropertyResolver("classpath:non-existent.properties")
        assertFalse(resolver.isLoaded)
        assertNull(resolver.resolve("test.property"))
    }

    @Test
    fun `should load properties from file system`() {
        // Create a temporary file
        val tempFile = File.createTempFile("test-properties", ".properties")
        tempFile.deleteOnExit()

        // Write test properties to the file
        tempFile.writeText("file.property1=fileValue1\nfile.property2=fileValue2")

        // Test with file path
        val resolver = PropertyResolver(tempFile.absolutePath)
        assertTrue(resolver.isLoaded)
        assertEquals("fileValue1", resolver.resolve("file.property1"))
        assertEquals("fileValue2", resolver.resolve("file.property2"))
    }

    @Test
    fun `should load properties with file prefix`() {
        // Create a temporary file
        val tempFile = File.createTempFile("test-properties-file", ".properties")
        tempFile.deleteOnExit()

        // Write test properties to the file
        tempFile.writeText("file.prefix.property=prefixValue")

        // Test with file: prefix
        val resolver = PropertyResolver("file:${tempFile.absolutePath}")
        assertTrue(resolver.isLoaded)
        assertEquals("prefixValue", resolver.resolve("file.prefix.property"))
    }

    @Test
    fun `should get all properties`() {
        val resolver = PropertyResolver("classpath:test-properties.properties")
        val allProps = resolver.getAllProperties()

        assertEquals("value1", allProps["test.property1"])
        assertEquals("value2", allProps["test.property2"])
    }
}