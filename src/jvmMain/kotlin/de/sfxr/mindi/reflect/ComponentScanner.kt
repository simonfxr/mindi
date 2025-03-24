package de.sfxr.mindi.reflect

import de.sfxr.mindi.Component
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile

internal typealias ClassSet = MutableMap<String, Class<*>>

/**
 * Utility object for scanning the classpath to find components annotated with specific annotations.
 */
object ComponentScanner {
    /**
     * Finds components in the specified package prefixes by scanning for classes with component annotations.
     *
     * @param packagePrefixes List of package prefixes to scan recursively for components (e.g., "com.example").
     * @param reflector The reflector to use for component detection and reflection, defaults to [Reflector.Default].
     * @param classLoader Optional class loader for scanning, defaults to the current thread's context class loader.
     * @return List of [Component] instances found in the specified package prefixes.
     */
    fun findComponents(
        packagePrefixes: List<String>,
        reflector: Reflector = Reflector.Default,
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader,
    ): List<Component<*>> {

        val annotationClasses = reflector.componentAnnotations.map { it.klass.java }

        val annotationNames = annotationClasses.map {
            ("L" + it.name.replace('.', '/') + ";").toByteArray(StandardCharsets.UTF_8)
        }

        val annotatedClassesSet = mutableMapOf<String, Class<*>>()
        packagePrefixes.sortedByDescending { it.length }.forEach { prefix ->
            val packagePath = prefix.replace('.', '/')
            classLoader.resources(packagePath).forEach { resource ->
                when (resource.protocol) {
                    "file" -> scanFileSystem(
                        Paths.get(resource.toURI()),
                        prefix,
                        annotationClasses,
                        annotationNames,
                        annotatedClassesSet,
                        classLoader
                    )

                    "jar" -> scanJarFile(
                        resource,
                        packagePath,
                        prefix,
                        annotationClasses,
                        annotationNames,
                        annotatedClassesSet,
                        classLoader
                    )
                }
            }
        }

        return annotatedClassesSet.values
            .sortedBy { "${it.packageName}~~~${it.simpleName}" }
            .map { reflector.reflect(it.kotlin) }
    }

    /**
     * Scans a file system directory for annotated classes.
     *
     * @param dirPath The base directory path corresponding to the package prefix.
     * @param packagePrefix The package prefix (e.g., "com.example").
     * @param annotationClasses List of annotations to check for.
     * @param annotatedClasses Thread-safe set to collect annotated classes.
     * @param classLoader Class loader to load classes.
     */
    private fun scanFileSystem(
        dirPath: Path,
        packagePrefix: String,
        annotationClasses: List<Class<out Annotation>>,
        annotationNames: List<ByteArray>,
        annotatedClasses: ClassSet,
        classLoader: ClassLoader,
    ) {
        if (!Files.exists(dirPath)) return
        Files.walk(dirPath)
            .filter { it.isRegularFile() && it.toString().endsWith(".class") }
            .forEach { path ->
                val className = getClassNameFromPath(dirPath, path, packagePrefix)
                checkAndAddClass(className, annotationClasses, annotationNames, annotatedClasses, classLoader)
            }
    }

    /**
     * Scans a JAR file resource for annotated classes, handling both standard and nested JARs.
     *
     * @param resource The URL of the JAR resource.
     * @param packagePath The package path (e.g., "com/example").
     * @param packagePrefix The package prefix (e.g., "com.example").
     * @param annotationClasses List of annotations to check for.
     * @param annotatedClasses Thread-safe set to collect annotated classes.
     * @param classLoader Class loader to load classes.
     */
    private fun scanJarFile(
        resource: URL,
        packagePath: String,
        packagePrefix: String,
        annotationClasses: List<Class<out Annotation>>,
        annotationNames: List<ByteArray>,
        annotatedClasses: ClassSet,
        classLoader: ClassLoader,
    ) {
        val urlPath = resource.toString()
        val jarPath: String
        val internalPath: String

        when {
            urlPath.startsWith("jar:file:") -> {
                val exclamationIndex = urlPath.indexOf("!")
                if (exclamationIndex != -1) {
                    jarPath = java.net.URLDecoder.decode(urlPath.substring(9, exclamationIndex), StandardCharsets.UTF_8)
                    internalPath = if (exclamationIndex < urlPath.length - 1) {
                        urlPath.substring(exclamationIndex + 1)
                    } else {
                        ""
                    }
                } else {
                    jarPath = java.net.URLDecoder.decode(urlPath.substring(9), StandardCharsets.UTF_8)
                    internalPath = ""
                }
            }
            else -> {
                val path = resource.path
                val start = if (path.startsWith("file:")) 5 else 0
                val exclamationIndex = path.indexOf("!")
                if (exclamationIndex != -1) {
                    jarPath = path.substring(start, exclamationIndex)
                    internalPath = if (exclamationIndex < path.length - 1) {
                        path.substring(exclamationIndex + 1)
                    } else {
                        ""
                    }
                } else {
                    jarPath = path.substring(start)
                    internalPath = ""
                }
            }
        }

        if (internalPath.contains("!")) {
            scanNestedJarFile(
                jarPath,
                internalPath,
                packagePath,
                packagePrefix,
                annotationClasses,
                annotationNames,
                annotatedClasses,
                classLoader
            )
        } else {
            scanStandardJarFile(
                Paths.get(jarPath),
                packagePath,
                packagePrefix,
                annotationClasses,
                annotationNames,
                annotatedClasses,
                classLoader
            )
        }
    }

    /**
     * Scans a standard (non-nested) JAR file for annotated classes.
     *
     * @param jarFile Path to the JAR file on the file system.
     * @param packagePath Package path within the JAR (e.g., "com/example").
     * @param packagePrefix Package prefix (e.g., "com.example").
     * @param annotationClasses List of annotations to check for.
     * @param annotatedClasses Thread-safe set to collect annotated classes.
     * @param classLoader Class loader to load classes.
     */
    private fun scanStandardJarFile(
        jarFile: Path,
        packagePath: String,
        packagePrefix: String,
        annotationClasses: List<Class<out Annotation>>,
        annotationNames: List<ByteArray>,
        annotatedClasses: ClassSet,
        classLoader: ClassLoader,
    ) {
        if (!Files.exists(jarFile))
            return
        val fs = FileSystems.newFileSystem(jarFile, classLoader)
        try {
            val jarRoot = fs.getPath(packagePath)
            if (!Files.exists(jarRoot))
                return
            Files.walk(jarRoot)
                .filter { it.isRegularFile() && it.toString().endsWith(".class") }
                .forEach { path ->
                    val className = getClassNameFromPath(jarRoot, path, packagePrefix)
                    checkAndAddClass(className, annotationClasses, annotationNames, annotatedClasses, classLoader)
                }
        } finally {
            fs.close()
        }
    }

    /**
     * Scans a nested JAR file (e.g., a JAR within another JAR) for annotated classes.
     * Note: This implementation handles one level of nesting; multi-level nesting could be added in the future.
     *
     * @param outerJarPath Path to the outer JAR file.
     * @param internalPath Internal path within the outer JAR (e.g., "/lib/nested.jar!/com/example").
     * @param packagePath Default package path if not specified in internalPath.
     * @param packagePrefix Package prefix (e.g., "com.example").
     * @param annotationClasses List of annotations to check for.
     * @param annotatedClasses Thread-safe set to collect annotated classes.
     * @param classLoader Class loader to load classes.
     */
    internal fun scanNestedJarFile(
        outerJarPath: String,
        internalPath: String,
        packagePath: String,
        packagePrefix: String,
        annotationClasses: List<Class<out Annotation>>,
        annotationNames: List<ByteArray>,
        annotatedClasses: ClassSet,
        classLoader: ClassLoader,
    ) {
        // Increment counter for test verification
        nestedJarScannedCount++

        val parts = internalPath.split("!", limit = 2)
        val nestedJarPath = parts[0].removePrefix("/")
        val nestedPackagePath = if (parts.size > 1) parts[1].removePrefix("/") else packagePath

        val outerJarFile = Paths.get(outerJarPath)
        val fs = FileSystems.newFileSystem(outerJarFile, classLoader)
        try {
            val nestedJarFile = fs.getPath(nestedJarPath)
            if (!Files.exists(nestedJarFile))
                return

            scanStandardJarFile(
                nestedJarFile,
                nestedPackagePath,
                packagePrefix,
                annotationClasses,
                annotationNames,
                annotatedClasses,
                classLoader
            )
        } finally {
            fs.close()
        }
    }

    /**
     * Extracts the fully qualified class name from a file path relative to a base path.
     *
     * @param basePath The base path corresponding to the package prefix (e.g., "/com/example").
     * @param path The full path to the class file (e.g., "/com/example/sub/ClassName.class").
     * @param packagePrefix The package prefix (e.g., "com.example").
     * @return The fully qualified class name (e.g., "com.example.sub.ClassName").
     */
    private fun getClassNameFromPath(basePath: Path, path: Path, packagePrefix: String): String {
        val relativePath = basePath.relativize(path).toString()
            .replace('/', '.')
            .replace('\\', '.')
            .dropLast(6) // Remove ".class"
        return "$packagePrefix.$relativePath"
    }

    /**
     * Checks if a class is annotated with any of the specified annotations and adds it to the set if so.
     *
     * @param className Fully qualified class name to check.
     * @param annotationClasses List of annotations to look for.
     * @param annotatedClasses Thread-safe set to collect annotated classes.
     * @param classLoader Class loader to load the class.
     */
    private fun checkAndAddClass(
        className: String,
        annotationClasses: List<Class<out Annotation>>,
        annotationNames: List<ByteArray>,
        annotatedClasses: ClassSet,
        classLoader: ClassLoader,
    ) {
        if (className in annotatedClasses)
            return
        if (!maybeAnnotated(className, annotationNames, classLoader))
            return
        val clazz = Class.forName(className, false, classLoader)
        for (annotationClass in annotationClasses) {
            if (clazz.isAnnotationPresent(annotationClass)) {
                annotatedClasses[className] = clazz
                return
            }
        }
    }

    /**
     * Heuristically determines if a class is likely annotated by checking its bytecode for annotation names.
     *
     * @param className Fully qualified class name.
     * @param annotationNames List of annotation names to check for.
     * @param classLoader Class loader to access the class resource.
     * @return True if the class is likely annotated, false otherwise.
     */
    private fun maybeAnnotated(
        className: String,
        annotationNames: List<ByteArray>,
        classLoader: ClassLoader,
    ): Boolean {
        val classResourceName = className.replace('.', '/') + ".class"
        val resource = classLoader.getResource(classResourceName) ?: return false
        resource.openStream().use { stream ->
            val bytes = stream.readBytes()
            for (name in annotationNames)
                if (containsBytes(bytes, name))
                    return true
        }
        return false
    }

    /**
     * Checks if a byte array contains a given byte array.
     */
    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.size > haystack.size)
            return false
        next@for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices)
                if (haystack[i + j] != needle[j])
                    continue@next
            return true
        }
        return false
    }

    // Statistics for testing purposes
    internal var nestedJarScannedCount = 0
        private set
}