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
 * Checks if an element has any of the specified annotations.
 *
 * @param T The type of value extracted from the annotations
 * @param annotations List of annotation types to check for
 * @return True if the element has any of the specified annotations
 */
internal fun <T> KAnnotatedElement.hasAnyAnnotation(annotations: List<AnnotationOf<T>>): Boolean =
    annotations.any { a -> findAnnotations(a.klass).isNotEmpty() }
