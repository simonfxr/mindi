package de.sfxr.mindi.reflect

import kotlin.reflect.KClass

/** Annotation that doesn't extract a specific value (just checks for presence) */
typealias TagAnnotation = AnnotationOf<Unit>

/**
 * Represents an annotation type and a function to extract a value from it.
 *
 * This class helps generalize annotation processing by coupling an annotation type
 * with a way to extract a specific value from it.
 *
 * @param T The type of value extracted from the annotation
 * @property klass The annotation class
 * @property valueOf Function to extract a value from an annotation instance
 */
@ConsistentCopyVisibility
data class AnnotationOf<out T> private constructor(val klass: KClass<out Annotation>, internal val valueOf: (Annotation) -> T) {
    companion object {
        /**
         * Creates a TagAnnotation for the specified annotation class.
         *
         * @param A The annotation type
         * @param klass The annotation class
         * @return A TagAnnotation for the specified class
         */
        fun <A: Annotation> tagged(klass: KClass<A>): TagAnnotation = AnnotationOf<Unit>(klass) {}

        /**
         * Creates a TagAnnotation for the specified annotation class.
         *
         * @param A The annotation type
         * @param klass The annotation class
         * @return A TagAnnotation for the specified class
         */
        inline fun <reified A: Annotation> tagged(): TagAnnotation = tagged(A::class)

        /**
         * Creates a ValueAnnotation for the specified annotation class.
         *
         * @param A The annotation type
         * @param T The value type contained in the annotation
         * @param v Function to extract a value from the annotation
         * @return A AnnotationOf<T> for the specified class
         */
        inline fun <reified A: Annotation, T> valued(noinline v: (A) -> T): AnnotationOf<T> =
            valued(A::class, v)

        /**
         * Creates a ValueAnnotation for the specified annotation class.
         *
         * @param A The annotation type
         * @param T The value type contained in the annotation
         * @param klass The annotation class
         * @param v Function to extract a value from the annotation
         * @return A AnnotationOf for the specified class
         */
        @Suppress("UNCHECKED_CAST")
        fun <A: Annotation, T> valued(klass: KClass<A>, v: (A) -> T): AnnotationOf<T> =
            AnnotationOf<T>(klass) { v(it as A) }
    }
}
