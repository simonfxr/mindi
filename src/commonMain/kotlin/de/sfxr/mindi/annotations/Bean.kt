package de.sfxr.mindi.annotations

/**
 * Marks a factory method as a bean definition that can be discovered by the dependency injection system.
 *
 * When this annotation is applied to a method in an object or class, the method will be treated
 * as a factory method for creating components. The method's return type becomes the component type,
 * and its parameters are treated as dependencies.
 *
 * Factory methods provide an alternative to direct class instantiation, allowing more flexibility
 * in how components are created. To use this feature:
 *
 * 1. Define an object or class with methods annotated with @Bean
 * 2. Use Reflector.reflectFactory(yourObject) to get Component definitions for all bean methods
 * 3. Add these components to your Context
 *
 * Bean methods allow component definitions to be declared programmatically rather than using
 * component classes directly. This is especially useful when:
 * 1. You need to configure third-party classes that you can't modify
 * 2. You want to apply conditional logic to component creation
 * 3. You want to group related component definitions together
 *
 * **Important note**: Unlike Spring's @Bean methods, Mindi's implementation does not use runtime
 * proxies to cache bean instances. This means if one @Bean method calls another @Bean method
 * directly, it will create a new instance each time rather than returning the managed singleton.
 * To share instances between beans, you should inject them as method parameters rather than
 * calling @Bean methods directly.
 *
 * Example usage:
 * ```kotlin
 * object AppConfig {
 *   // Simple bean with no dependencies
 *   @Bean
 *   fun dataSource(): DataSource {
 *     return BasicDataSource().apply {
 *       url = "jdbc:h2:mem:test"
 *       username = "sa"
 *     }
 *   }
 *
 *   // Bean with dependencies automatically injected - proper way to use other beans
 *   @Bean
 *   fun userService(dataSource: DataSource): UserService {
 *     return UserServiceImpl(dataSource)  // dataSource is injected, not called directly
 *   }
 *
 *   // Named bean (explicit component name)
 *   @Bean("authenticationProvider")
 *   fun createAuthProvider(): AuthenticationProvider {
 *     return CustomAuthProvider()
 *   }
 *
 *   // AVOID this pattern - it will create a new dataSource instance, not use the singleton
 *   @Bean
 *   fun badUserRepository(): UserRepository {
 *     // Don't do this! It creates a new dataSource instance each time
 *     val ds = dataSource()  // Direct call creates a new instance, not the managed bean!
 *     return JdbcUserRepository(ds)
 *   }
 *
 *   // CORRECT pattern - inject dependencies as parameters
 *   @Bean
 *   fun goodUserRepository(dataSource: DataSource): UserRepository {
 *     return JdbcUserRepository(dataSource)  // Uses the managed singleton
 *   }
 * }
 * ```
 *
 * @property value Optional name for the bean (defaults to method name)
 *             This name can be used with @Qualifier to select a specific implementation
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
annotation class Bean(val value: String = "")