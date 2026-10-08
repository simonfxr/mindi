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
    val result = flatMapTo(linkedSetOf()) { a -> element.findAnnotations(a.klass).map { a.valueOf(it) } }
    element.annotations.mapNotNullTo(result) { a ->
        // If this annotation type is itself annotated with @Qualifier
        a.takeIf { annotated(a.annotationClass) }
    }
    return result
}

/**
 * Resolves one qualifier across all annotation sites of an injection point.
 *
 * @param elements The annotated elements belonging to the injection point
 * @return The unique qualifier value, or null if no qualifier annotations are found
 * @throws IllegalArgumentException If more than one distinct qualifier is present
 * @see allQualifiers
 */
internal fun Iterable<AnnotationOf<Any>>.singleQualifier(vararg elements: KAnnotatedElement?): Any? {
    val qualifiers = elements.filterNotNull().flatMapTo(linkedSetOf()) { allQualifiers(it) }
    require(qualifiers.size <= 1) {
        "Multiple qualifiers $qualifiers on injection point ${elements.first()}"
    }
    return qualifiers.singleOrNull()
}

/**
 * Checks if an element has any of the specified annotations.
 *
 * @param element The annotated element to inspect
 * @return True if the element has any of the specified annotations
 */
internal fun Iterable<AnnotationOf<*>>.annotated(element: KAnnotatedElement): Boolean =
    any { a -> element.findAnnotations(a.klass).isNotEmpty() }
