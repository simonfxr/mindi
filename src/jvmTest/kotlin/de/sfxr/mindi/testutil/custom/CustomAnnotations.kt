package de.sfxr.mindi.testutil.custom

/**
 * Custom annotation for testing multiple annotation scanning
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomComponent(val value: String = "")

/**
 * Another custom annotation for testing multiple annotation scanning
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnotherComponent(val value: String = "")