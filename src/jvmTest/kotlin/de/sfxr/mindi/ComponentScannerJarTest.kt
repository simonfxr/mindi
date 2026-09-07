package de.sfxr.mindi

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.reflect.ComponentScanner
import de.sfxr.mindi.testutil.ComponentScannerTestComponent
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComponentScannerJarTest {
    @Test
    fun findsComponentInJarWhoseFilenameContainsPlusAndSpaces() = withScannerTempDirectory { directory ->
        val fixture = ComponentScannerTestComponent::class.java
        val packageName = fixture.packageName
        val packagePath = packageName.replace('.', '/')
        val classPath = fixture.name.replace('.', '/') + ".class"
        val jar = directory.resolve("components + literal plus!.jar")
        writeScannerJar(jar, mapOf(classPath to scannerFixtureBytes()))

        IsolatedScannerJarLoader(jar, packageName).use { loader ->
            val resources = Collections.list(loader.getResources(packagePath))
            assertEquals(1, resources.size)
            assertEquals("jar", resources.single().protocol)
            assertTrue(resources.single().toExternalForm().contains("components%20+%20literal%20plus!.jar"))
            assertEquals("jar", assertNotNull(loader.getResource(classPath)).protocol)

            val components = ComponentScanner.findComponents(listOf(packageName), classLoader = loader)
            assertEquals(listOf(fixture.name), components.map { it.klass.java.name })
            assertSame(loader, components.single().klass.java.classLoader)
            assertEquals(jar.toUri().toURL(), components.single().klass.java.protectionDomain.codeSource.location)
        }
    }

    @Test
    fun discoveryDoesNotLoadUnannotatedClassesOrInitializeJavaComponents() = withScannerTempDirectory { directory ->
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "Tests require the configured JDK 11 compiler")
        val packageName = "scanner.lazyfixture"
        val sources = directory.resolve("sources")
        val classes = directory.resolve("classes")
        Files.createDirectories(sources)
        Files.createDirectories(classes)
        val marker = directory.resolve("initialized")
        val markerLiteral = marker.toString().replace("\\", "\\\\").replace("\"", "\\\"")
        val annotated = sources.resolve("LazyComponent.java")
        Files.writeString(annotated, """
            package $packageName;
            @de.sfxr.mindi.annotations.Component
            public class LazyComponent {
                static {
                    try { java.nio.file.Files.createFile(java.nio.file.Paths.get("$markerLiteral")); }
                    catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
                }
                public LazyComponent() {}
            }
        """.trimIndent())
        val plain = sources.resolve("Unannotated.java")
        Files.writeString(plain, "package $packageName; public class Unannotated {}")
        compiler.getStandardFileManager(null, null, null).use { manager ->
            val annotationLocation = Path.of(Component::class.java.protectionDomain.codeSource.location.toURI())
            val options = listOf("--release", "11", "-classpath", annotationLocation.toString(), "-d", classes.toString())
            assertTrue(compiler.getTask(null, manager, null, options, null,
                manager.getJavaFileObjects(annotated.toFile(), plain.toFile())).call(), "Compile real Java fixtures")
        }
        val classPaths = listOf("${packageName.replace('.', '/')}/LazyComponent.class", "${packageName.replace('.', '/')}/Unannotated.class")
        val jar = directory.resolve("lazy.jar")
        writeScannerJar(jar, classPaths.associateWith { Files.readAllBytes(classes.resolve(it)) })
        IsolatedScannerJarLoader(jar, packageName).use { loader ->
            val components = ComponentScanner.findComponents(listOf(packageName), classLoader = loader)
            assertEquals(listOf("$packageName.LazyComponent"), components.map { it.klass.java.name })
            assertSame(loader, components.single().klass.java.classLoader)
            assertFalse("$packageName.Unannotated" in loader.loadRequests, "Unannotated bytecode must not cause class loading")
            assertFalse(Files.exists(marker), "Discovery must not execute a Java component's static initializer")
            Class.forName("$packageName.LazyComponent", true, loader)
            assertTrue(Files.exists(marker), "The initializer must run when initialization is explicitly requested")
        }
    }
}

/** Keep fixture resources and classes out of the parent loader's compiled test directory. */
private class IsolatedScannerJarLoader(jar: Path, private val packageName: String) :
    URLClassLoader(arrayOf(jar.toUri().toURL()), ComponentScannerJarTest::class.java.classLoader) {
    private val packagePath = packageName.replace('.', '/')
    val loadRequests = mutableListOf<String>()

    private fun ownsResource(name: String) = name == packagePath || name.startsWith("$packagePath/")

    override fun getResources(name: String): Enumeration<URL> =
        if (ownsResource(name)) findResources(name) else super.getResources(name)

    override fun getResource(name: String): URL? =
        if (ownsResource(name)) findResource(name) else super.getResource(name)

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        loadRequests.add(name)
        if (!name.startsWith("$packageName.")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            val klass = findLoadedClass(name) ?: findClass(name)
            if (resolve) resolveClass(klass)
            return klass
        }
    }
}

internal fun scannerFixtureBytes(): ByteArray {
    val fixture = ComponentScannerTestComponent::class.java
    return assertNotNull(fixture.getResourceAsStream("/${fixture.name.replace('.', '/')}.class"))
        .use { it.readBytes() }
}

internal fun writeScannerJar(path: Path, entries: Map<String, ByteArray>) {
    JarOutputStream(Files.newOutputStream(path)).use { output ->
        val directories = entries.keys.flatMap { name ->
            name.indices.filter { name[it] == '/' }.map { name.substring(0, it + 1) }
        }.distinct()
        for (directory in directories) {
            output.putNextEntry(JarEntry(directory))
            output.closeEntry()
        }
        for ((name, bytes) in entries) {
            output.putNextEntry(JarEntry(name))
            output.write(bytes)
            output.closeEntry()
        }
    }
}

internal fun withScannerTempDirectory(test: (Path) -> Unit) {
    val directory = Files.createTempDirectory("mindi-scanner-")
    try {
        test(directory)
    } finally {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }
}
