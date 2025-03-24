package de.sfxr.mindi.annotations

/**
 * Specifies the name of a component or the name of a dependency to inject.
 *
 * Qualifiers are used to disambiguate between multiple components of the same type.
 * They can be applied to:
 * - Components to give them a name
 * - Injection points to specify which named component to inject
 *
 * When used on a component, it provides an additional name beyond the one specified
 * by the @Component annotation. When used on an injection point, it indicates which
 * specific component should be injected.
 *
 * Example usage:
 * ```kotlin
 * // Defining components
 * @Component("db")
 * @Qualifier("mysql")
 * class MySqlRepository : Repository { ... }
 *
 * @Component("db")
 * @Qualifier("postgres")
 * class PostgresRepository : Repository { ... }
 *
 * // Injecting a specific component
 * @Component
 * class DataService {
 *     @Autowired
 *     @Qualifier("postgres")
 *     lateinit var repository: Repository
 * }
 * ```
 *
 * @property value The qualifier name
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class Qualifier(val value: String = "")