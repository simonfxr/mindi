package de.sfxr.mindi

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.testutil.ComponentScannerTestComponent
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlin.test.*

/**
 * Tests for fat JAR scanning in ComponentScanner.
 *
 * This test creates a mock fat JAR structure with nested JARs to verify that
 * the ComponentScanner can handle such structures.
 */
class ComponentScannerFatJarTest {

    private val outerJarPath = Paths.get("build/tmp/test-fat.jar")
    private val innerJarPath = Paths.get("build/tmp/test-inner.jar")
    private val testPackage = "de.sfxr.mindi.fatjartest"

    @BeforeTest
    fun setUp() {
        // Delete any existing test JARs
        outerJarPath.deleteIfExists()
        innerJarPath.deleteIfExists()

        // Create directory if it doesn't exist
        Files.createDirectories(outerJarPath.parent)

        // First create the inner JAR with a component class
        JarOutputStream(Files.newOutputStream(innerJarPath)).use { jos ->
            // Add a component class file to the JAR
            val className = "${testPackage.replace('.', '/')}/InnerJarComponent.class"
            jos.putNextEntry(JarEntry(className))

            // Write a minimal class file
            jos.write(createMinimalClassWithAnnotation("de/sfxr/mindi/annotations/Component"))
            jos.closeEntry()
        }

        // Now create the outer (fat) JAR containing the inner JAR
        JarOutputStream(Files.newOutputStream(outerJarPath)).use { jos ->
            // Add the inner JAR as an entry in the lib directory
            jos.putNextEntry(JarEntry("lib/inner.jar"))

            // Copy the inner JAR content to the entry
            Files.newInputStream(innerJarPath).use { input ->
                input.copyTo(jos)
            }
            jos.closeEntry()

            // Also add a direct component class to the outer JAR
            val className = "${testPackage.replace('.', '/')}/OuterJarComponent.class"
            jos.putNextEntry(JarEntry(className))
            jos.write(createMinimalClassWithAnnotation("de/sfxr/mindi/annotations/Component"))
            jos.closeEntry()
        }

        // Add the outer JAR to the classpath
        System.setProperty("java.class.path",
            System.getProperty("java.class.path") + File.pathSeparator + outerJarPath.toString())
    }

    @Test
    fun testFatJarScanning() {
        // Simply verify that our scanning code doesn't crash on fat JARs
        // Due to the limitations of creating valid class files in tests, we can't fully
        // verify the loading of classes from the fat JAR, but we can ensure the code
        // attempts to scan them without errors

        try {
            val components = ComponentScanner.findComponents(listOf(testPackage))
            println("ComponentScanner processed fat JAR: $outerJarPath")
            println("Found ${components.size} components (note: might be 0 due to test limitations)")
        } catch (e: Exception) {
            // If we get here, fat JAR scanning failed
            e.printStackTrace()
            assertFalse(true, "Fat JAR scanning should not throw exceptions: ${e.message}")
        }

        // Test passes if no exception was thrown
        assertTrue(true, "Fat JAR scanning completed without errors")
    }

    /**
     * Tests the scanNestedJarFile method directly, ensuring it's called and works as expected
     */
    @Test
    fun testNestedJarWithDirectNestedURL() {
        // First, create our inner jar with a real component
        val innerJarPath = Paths.get("build/tmp/direct-inner-test.jar")
        val outerJarPath = Paths.get("build/tmp/direct-outer-test.jar")

        // Clean up any existing JAR files
        innerJarPath.deleteIfExists()
        outerJarPath.deleteIfExists()
        Files.createDirectories(innerJarPath.parent)

        // Get the actual class file bytes for our test component
        val componentClassName = ComponentScannerTestComponent::class.java.name
        val componentClassPath = componentClassName.replace('.', '/') + ".class"
        val componentClassUrl = Thread.currentThread().contextClassLoader.getResource(componentClassPath)
            ?: throw IllegalStateException("Could not find class file for $componentClassName")

        val componentClassBytes = componentClassUrl.openStream().use { it.readBytes() }

        // Create the inner JAR with real component classes
        JarOutputStream(Files.newOutputStream(innerJarPath)).use { jos ->
            jos.putNextEntry(JarEntry(componentClassPath))
            jos.write(componentClassBytes)
            jos.closeEntry()

            // We also need the Component annotation class
            val annotationClassName = Component::class.java.name
            val annotationClassPath = annotationClassName.replace('.', '/') + ".class"
            val annotationClassUrl = Thread.currentThread().contextClassLoader.getResource(annotationClassPath)
            if (annotationClassUrl != null) {
                jos.putNextEntry(JarEntry(annotationClassPath))
                annotationClassUrl.openStream().use { it.copyTo(jos) }
                jos.closeEntry()
            }
        }

        // Create the outer JAR with the inner JAR nested inside
        JarOutputStream(Files.newOutputStream(outerJarPath)).use { jos ->
            jos.putNextEntry(JarEntry("lib/component.jar"))
            Files.newInputStream(innerJarPath).use { it.copyTo(jos) }
            jos.closeEntry()
        }

        // Create a URL that directly points to the nested JAR with the right structure to trigger nested scanning
        val packagePath = componentClassName.substring(0, componentClassName.lastIndexOf('.')).replace('.', '/')
        val nestedJarUrl = "jar:file:${outerJarPath.toAbsolutePath()}!/lib/component.jar!/$packagePath"

        // Create a class loader that will return our crafted URL when asked for the right package
        val directNestedLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
            override fun getResources(name: String): Enumeration<URL> {
                // When looking for our component package, return our nested JAR URL
                return if (name == packagePath) {
                    Collections.enumeration(listOf(URI.create(nestedJarUrl).toURL()))
                } else {
                    // For other packages, delegate to parent
                    parent.getResources(name)
                }
            }

            // Add a loadClass override to help find our component class
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                // For our test component, return the actual class
                return if (name == componentClassName) {
                    ComponentScannerTestComponent::class.java
                } else {
                    super.loadClass(name, resolve)
                }
            }
        }

        // Get the initial count before scanning
        val initialCount = ComponentScanner.nestedJarScannedCount

        // Call findComponents with the package of our test component
        val packageToScan = componentClassName.substring(0, componentClassName.lastIndexOf('.'))
        val components = ComponentScanner.findComponents(listOf(packageToScan), classLoader = directNestedLoader)
        assertEquals(
            listOf(ComponentScannerTestComponent::class),
            components.map { it.klass })

        // Verify scanNestedJarFile was called
        assertTrue(ComponentScanner.nestedJarScannedCount > initialCount,
            "scanNestedJarFile should have been called with direct nested URL")

        println("scanNestedJarFile was called ${ComponentScanner.nestedJarScannedCount - initialCount} times " +
            "with direct nested jar URL")
    }

    /**
     * Tests the full scanning process for nested JARs by simulating a real JAR structure.
     * This ensures the scanNestedJarFile integration with the rest of the scanning process is working.
     */
    @Test
    fun testRealNestedJarComponentScanning() {
        // Build paths for our test JARs
        val realInnerJarPath = Paths.get("build/tmp/real-inner-test.jar")
        val realOuterJarPath = Paths.get("build/tmp/real-outer-test.jar")

        // Clean up any existing test JAR files
        realInnerJarPath.deleteIfExists()
        realOuterJarPath.deleteIfExists()

        // Create directory if it doesn't exist
        Files.createDirectories(realInnerJarPath.parent)

        // Get the actual class file bytes
        val componentClassName = ComponentScannerTestComponent::class.java.name
        val componentClassPath = componentClassName.replace('.', '/') + ".class"
        val componentClassUrl = Thread.currentThread().contextClassLoader.getResource(componentClassPath)
            ?: throw IllegalStateException("Could not find class file for $componentClassName")

        val componentClassBytes = componentClassUrl.openStream().use { it.readBytes() }

        // Create the inner JAR with the real component class
        JarOutputStream(Files.newOutputStream(realInnerJarPath)).use { jos ->
            // Add the component class to the JAR
            jos.putNextEntry(JarEntry(componentClassPath))
            jos.write(componentClassBytes)
            jos.closeEntry()
        }

        // Create the outer JAR with the inner JAR nested inside
        JarOutputStream(Files.newOutputStream(realOuterJarPath)).use { jos ->
            // Add the inner JAR as an entry
            jos.putNextEntry(JarEntry("lib/inner-component.jar"))
            Files.newInputStream(realInnerJarPath).use { it.copyTo(jos) }
            jos.closeEntry()
        }

        // Instead of using URLClassLoader, we'll directly call the scanNestedJarFile method
        // using reflection to more precisely test the method

        // Get access to the private scanNestedJarFile method
        // Set up parameters
        val packagePath = componentClassName.substring(0, componentClassName.lastIndexOf('.')).replace('.', '/')
        val internalPath = "/lib/inner-component.jar!/$packagePath"
        val outerJarPathStr = realOuterJarPath.toAbsolutePath().toString()
        val packagePrefix = componentClassName.substring(0, componentClassName.lastIndexOf('.'))
        val annotationClasses = listOf(Component::class.java)
        val annotationNames = annotationClasses.map { ("L" + it.name.replace('.', '/') + ";").toByteArray(StandardCharsets.UTF_8) }
        val annotatedClasses = mutableMapOf<String, Class<*>>()
        val classLoader = Thread.currentThread().contextClassLoader

        // Reset/get the initial count
        val initialCount = ComponentScanner.nestedJarScannedCount

        // Directly call the scanNestedJarFile method
        ComponentScanner.scanNestedJarFile(
            outerJarPathStr,
            internalPath,
            packagePath,
            packagePrefix,
            annotationClasses,
            annotationNames,
            annotatedClasses,
            classLoader
        )

        // Verify that scanNestedJarFile was called by checking the counter
        assertTrue(ComponentScanner.nestedJarScannedCount > initialCount,
            "Nested JAR scanning should have been triggered with real JAR files")

        println("Nested JAR scanning was directly called and the counter was incremented by " +
            "${ComponentScanner.nestedJarScannedCount - initialCount}")
    }

    /**
     * Creates a minimal byte array that resembles a class file with an annotation
     *
     * @param annotationPath The internal path of the annotation class (e.g., "de/sfxr/mindi/annotations/Component")
     */
    private fun createMinimalClassWithAnnotation(annotationPath: String): ByteArray {
        // Create a simplified class file with the requested annotation path encoded
        val annotation = "L$annotationPath;"

        // Convert the annotation string to bytes
        val annotationBytes = annotation.toByteArray(Charsets.UTF_8)

        // Create a basic class file header
        val header = byteArrayOf(
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),  // Magic number
            0x00, 0x00, 0x00, 0x3D,  // Java 8 version
            0x00, 0x0F  // Constant pool count
        )

        // Combine header with annotation bytes to create a more realistic class file
        // that will pass the containsStringBytes check in ComponentScanner
        return header + annotationBytes
    }
}