package de.sfxr.mindi.annotations

/**
 * Marks a field, property, setter, constructor, or function for automatic dependency injection.
 *
 * The autowired elements will be automatically populated with matching components
 * from the dependency injection context. When multiple components of the required type
 * exist, the framework will try to resolve the ambiguity using @Qualifier annotations
 * or the @Primary marker.
 *
 * Dependency resolution works in the following order:
 * 1. If only one component matches the required type, it's used automatically
 * 2. If multiple components match, but one is marked with @Primary, it's used
 * 3. If multiple components match and a @Qualifier is specified, the qualified component is used
 * 4. If multiple components match and none of the above conditions are met, an exception is thrown
 *
 * It's also possible to inject all instances of a particular type by using Map<String, Type>
 * as the injection target type.
 *
 * Example usage:
 * ```kotlin
 * @Component
 * class MyService {
 *     // Field injection
 *     @Autowired
 *     lateinit var dependency: SomeDependency
 *
 *     // Constructor injection (preferred method)
 *     @Autowired
 *     constructor(otherDep: OtherDependency) { ... }
 *
 *     // Setter injection
 *     @Autowired
 *     fun setAnotherDep(dep: AnotherDependency) { ... }
 *
 *     // Injecting all implementations of an interface
 *     @Autowired
 *     lateinit var allHandlers: Map<String, EventHandler>
 * }
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class Autowired(val required: Boolean = true)
