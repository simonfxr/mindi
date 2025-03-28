package de.sfxr.mindi.reflect

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.full.findAnnotations

/**
 * Finds the first annotation from a list of annotation types and extracts its value.
 *
 * @param T The type of value to extract
 * @param annotations List of annotation types to search for
 * @return The extracted value, or null if no matching annotation is found
 */
internal fun <T> KAnnotatedElement.findFirstAnnotation(annotations: List<AnnotationOf<T>>): T? =
    annotations.firstNotNullOfOrNull { a -> findAnnotations(a.klass).firstOrNull()?.let { a.valueOf(it) } }

/**
 * Finds all annotations from a list of annotation types and extracts their values.
 *
 * @param T The type of value to extract
 * @param annotations List of annotation types to search for
 * @return List of extracted values from all matching annotations
 */
internal fun <T> KAnnotatedElement.findAllAnnotations(annotations: List<AnnotationOf<T>>): List<T> =
    annotations.flatMap { a -> findAnnotations(a.klass).map { a.valueOf(it)} }

/**
 * Finds all qualifier annotations, including meta-annotations (annotations on annotations).
 * This allows for custom qualifier annotations like @Primary, @Repository, etc.
 *
 * @param annotations List of qualifier annotation types to search for
 * @return Set of qualifier values from all matching annotations, including meta-annotations
 */
internal fun KAnnotatedElement.findAllQualifierAnnotations(annotations: List<AnnotationOf<Any>>): Set<Any> {
    val result = annotations.flatMapTo(HashSet()) { a -> findAnnotations(a.klass).map { a.valueOf(it) } }
    this.annotations.mapNotNullTo(result) { a ->
        // If this annotation type is itself annotated with @Qualifier
        a.takeIf { a.annotationClass.hasAnyAnnotation(annotations) }
    }
    return result
}

/**
 * See [findAllQualifierAnnotations]
 */
internal fun KAnnotatedElement.findFirstQualifierAnnotations(annotations: List<AnnotationOf<Any>>) =
    findAllQualifierAnnotations(annotations).firstOrNull()

/**
 * Checks if an element has any of the specified annotations.
 *
 * @param T The type of value extracted from the annotations
 * @param annotations List of annotation types to check for
 * @return True if the element has any of the specified annotations
 */
internal fun <T> KAnnotatedElement.hasAnyAnnotation(annotations: List<AnnotationOf<T>>): Boolean =
    annotations.any { a -> findAnnotations(a.klass).isNotEmpty() }