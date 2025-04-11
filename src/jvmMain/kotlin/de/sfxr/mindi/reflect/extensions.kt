package de.sfxr.mindi.reflect

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.full.findAnnotations

/**
 * Finds the first annotation from a list of annotation extractors and extracts its value.
 *
 * @param T The type of value to extract
 * @param element The annotated element to inspect
 * @return The extracted value, or null if no matching annotation is found
 */
internal fun <T> Iterable<AnnotationOf<T>>.annotation(element: KAnnotatedElement): T? =
    firstNotNullOfOrNull { a -> element.findAnnotations(a.klass).firstOrNull()?.let { a.valueOf(it) } }

/**
 * Finds all qualifier annotations, including meta-annotations (annotations on annotations).
 * This allows for custom qualifier annotations like @Primary, @Repository, etc.
 *
 * @param element The annotated element to inspect
 * @return Set of qualifier values from all matching annotations, including meta-annotations
 */
internal fun Iterable<AnnotationOf<Any>>.allQualifiers(element: KAnnotatedElement): Set<Any> {
    val result = flatMapTo(HashSet()) { a -> element.findAnnotations(a.klass).map { a.valueOf(it) } }
    element.annotations.mapNotNullTo(result) { a ->
        // If this annotation type is itself annotated with @Qualifier
        a.takeIf { annotated(a.annotationClass) }
    }
    return result
}

/**
 * Returns the first qualifier found from the element's annotations.
 *
 * @param element The annotated element to inspect
 * @return The first qualifier value, or null if no qualifier annotations are found
 * @see allQualifiers
 */
internal fun Iterable<AnnotationOf<Any>>.firstQualifier(element: KAnnotatedElement) =
    allQualifiers(element).firstOrNull()

/**
 * Checks if an element has any of the specified annotations.
 *
 * @param element The annotated element to inspect
 * @return True if the element has any of the specified annotations
 */
internal fun Iterable<AnnotationOf<*>>.annotated(element: KAnnotatedElement): Boolean =
    any { a -> element.findAnnotations(a.klass).isNotEmpty() }