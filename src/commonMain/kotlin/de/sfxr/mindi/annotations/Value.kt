package de.sfxr.mindi.annotations

/**
 * Injects external values from a ValueResolver into fields, setter methods, or constructor parameters.
 *
 * The value can be:
 * 1. A literal value that will be converted to the target type (e.g., "8080" to an Int)
 * 2. A variable reference in the format ${variable} that will be resolved using a ValueResolver
 * 3. A variable with default value in the format ${variable:default} used when the variable isn't found
 *
 * By default, environment variables are used as the value source, but custom ValueResolver
 * implementations can be provided when creating a Context.
 *
 * Supported type conversions:
 * - String: direct value
 * - Int/Long: parsed as numbers
 * - Double/Float: parsed as decimal numbers
 * - Boolean: "true"/"false" (case-insensitive)
 * - Lists and basic collections: comma-separated values
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class ConfigService(
 *     // Literal value converted to Int
 *     @Value("8080") val port: Int,
 *
 *     // Environment variable DB_URL with no default
 *     @Value("\${db.url}") val dbUrl: String,
 *
 *     // Environment variable API_TIMEOUT with default 30000
 *     @Value("\${api.timeout:30000}") val timeout: Long
 * ) {
 *     // Field injection with default value
 *     @Value("\${app.name:MyApp}")
 *     lateinit var appName: String
 *
 *     // Setter injection with boolean conversion
 *     @Value("\${enable.feature:false}")
 *     fun setFeatureEnabled(enabled: Boolean) {
 *         // ...
 *     }
 * }
 * ```
 *
 * @property value The value expression to be resolved
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD,AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class Value(val value: String)
