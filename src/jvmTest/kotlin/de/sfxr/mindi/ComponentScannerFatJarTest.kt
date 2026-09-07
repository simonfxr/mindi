package de.sfxr.mindi

import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.testutil.ComponentScannerTestComponent
import java.io.ByteArrayInputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.nio.file.Files
import java.util.Collections
import java.util.Enumeration
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ComponentScannerFatJarTest {
    @Test
    fun discoversRealComponentInsideNestedJar() = withScannerTempDirectory { directory ->
        val fixture = ComponentScannerTestComponent::class.java
        val packagePath = fixture.packageName.replace('.', '/')
        val classPath = fixture.name.replace('.', '/') + ".class"
        val inner = directory.resolve("inner.jar")
        val outer = directory.resolve("outer + components.jar")
        writeScannerJar(inner, mapOf(classPath to scannerFixtureBytes()))
        writeScannerJar(outer, mapOf("BOOT-INF/lib/components.jar" to Files.readAllBytes(inner)))
        Files.delete(inner) // No standalone inner JAR remains available to accidentally scan or load.

        val nestedBytes = JarFile(outer.toFile()).use { jar ->
            jar.getInputStream(jar.getJarEntry("BOOT-INF/lib/components.jar")).use { it.readBytes() }
        }
        val classes = mutableMapOf<String, ByteArray>()
        JarInputStream(ByteArrayInputStream(nestedBytes)).use { jar ->
            var entry = jar.nextJarEntry
            while (entry != null) {
                if (!entry.isDirectory) classes[entry.name] = jar.readBytes()
                entry = jar.nextJarEntry
            }
        }
        val resource = URL("jar:${outer.toUri().toASCIIString()}!/BOOT-INF/lib/components.jar!/$packagePath")
        val loader = ArchiveFixtureLoader(fixture.packageName, resource, classes)
        assertEquals(listOf(resource), Collections.list(loader.getResources(packagePath)))
        val components = ComponentScanner.findComponents(listOf(fixture.packageName), classLoader = loader)
        assertEquals(listOf(fixture.name), components.map { it.klass.java.name })
        assertSame(loader, components.single().klass.java.classLoader)
    }

    @Test
    fun discoversComponentUnderBootInfClasses() = withScannerTempDirectory { directory ->
        val fixture = ComponentScannerTestComponent::class.java
        val classPath = fixture.name.replace('.', '/') + ".class"
        val outer = directory.resolve("boot application.jar")
        writeScannerJar(outer, mapOf("BOOT-INF/classes/$classPath" to scannerFixtureBytes()))
        val bytes = JarFile(outer.toFile()).use { jar ->
            jar.getInputStream(jar.getJarEntry("BOOT-INF/classes/$classPath")).use { it.readBytes() }
        }
        for (prefix in listOf(fixture.packageName, "")) {
            val scanPath = prefix.replace('.', '/')
            val resource = URL("jar:${outer.toUri().toASCIIString()}!/BOOT-INF/classes/$scanPath")
            val loader = ArchiveFixtureLoader(fixture.packageName, resource, mapOf(classPath to bytes), scanPath)
            assertEquals(listOf(resource), Collections.list(loader.getResources(scanPath)))
            val components = ComponentScanner.findComponents(listOf(prefix), classLoader = loader)
            assertEquals(listOf(fixture.name), components.map { it.klass.java.name }, "Scan prefix: '$prefix'")
            assertSame(loader, components.single().klass.java.classLoader)
        }
    }
}

/** A minimal fat-JAR loader: discovery sees only the archive URL and loading uses its real entry bytes. */
private class ArchiveFixtureLoader(
    private val packageName: String,
    private val packageResource: URL,
    private val entries: Map<String, ByteArray>,
    private val scanPath: String = packageName.replace('.', '/'),
) : ClassLoader(ComponentScannerFatJarTest::class.java.classLoader) {
    private val packagePath = packageName.replace('.', '/')

    override fun getResources(name: String): Enumeration<URL> = when {
        name == scanPath -> Collections.enumeration(listOf(packageResource))
        name.startsWith("$packagePath/") -> Collections.enumeration(listOfNotNull(getResource(name)))
        else -> super.getResources(name)
    }

    override fun getResource(name: String): URL? {
        if (name == packagePath) return packageResource
        if (!name.startsWith("$packagePath/")) return super.getResource(name)
        val bytes = entries[name] ?: return null
        // Java's built-in JAR URL handler cannot open nested entries. Serve the bytes read from the archive.
        val relativePath = if (scanPath.isEmpty()) name else name.removePrefix("$scanPath/")
        return URL(null, "${packageResource.toExternalForm().trimEnd('/')}/$relativePath",
            object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection = object : URLConnection(url) {
                    override fun connect() {}
                    override fun getInputStream() = ByteArrayInputStream(bytes)
                }
            })
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("$packageName.")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            val klass = findLoadedClass(name) ?: run {
                val bytes = assertNotNull(entries[name.replace('.', '/') + ".class"], "Missing archive class $name")
                defineClass(name, bytes, 0, bytes.size)
            }
            if (resolve) resolveClass(klass)
            return klass
        }
    }
}
