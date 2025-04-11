package de.sfxr.mindi.reflect

import de.sfxr.mindi.*
import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.internal.compact
import de.sfxr.mindi.internal.compose
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.javaField
import de.sfxr.mindi.annotations.Component as ComponentAnnotation

/**
 * Configuration for how reflection is used to process annotations.
 *
 * This class allows customization of which annotations are used for different
 * dependency injection features. The default implementation uses the standard
 * annotations provided by mindi, but these can be overridden if needed.
 *
 * @property autowiredAnnotations Annotations that mark fields/setters for dependency injection
 * @property componentAnnotations Annotations that mark classes as injectable components
 * @property postConstructAnnotations Annotations that mark methods to call after initialization
 * @property preDestroyAnnotations Annotations that mark methods to call before destruction
 * @property primaryAnnotations Annotations that mark a component as the primary implementation
 * @property valueAnnotations Annotations that mark fields/parameters for value injection
 * @property qualifierAnnotations Annotations used to qualify dependencies
 * @property eventListenerAnnotations Annotations that mark methods as event listeners
 * @property beanAnnotations Annotations that mark factory methods as bean definitions
 */
data class Reflector(
    val autowiredAnnotations: List<AnnotationOf<Boolean>>,
    val componentAnnotations: List<AnnotationOf<String>>,
    val postConstructAnnotations: List<TagAnnotation>,
    val preDestroyAnnotations: List<TagAnnotation>,
    val primaryAnnotations: List<TagAnnotation>,
    val valueAnnotations: List<AnnotationOf<String>>,
    val qualifierAnnotations: List<AnnotationOf<Any>>,
    val eventListenerAnnotations: List<TagAnnotation>,
    val beanAnnotations: List<AnnotationOf<String>>,
    val orderAnnotations: List<AnnotationOf<Int>>,
    /**
     * [ValueResolver] used to parse default values, it is not used for lookups,
     */
    val valueParser: ValueResolver=ValueResolver.Empty,
) {
    companion object {
        /**
         * Default reflection configuration using the standard mindi annotations
         */
        val Default = Reflector(
            autowiredAnnotations = listOf(AnnotationOf.valued<Autowired, _> { it.required }),
            componentAnnotations = listOf(AnnotationOf.valued<ComponentAnnotation, _> { it.value }),
            postConstructAnnotations = listOf(AnnotationOf.tagged<PostConstruct>()),
            preDestroyAnnotations = listOf(AnnotationOf.tagged<PreDestroy>()),
            primaryAnnotations = listOf(AnnotationOf.tagged<Primary>()),
            valueAnnotations = listOf(AnnotationOf.valued<Value, _> { it.value }),
            qualifierAnnotations = listOf(AnnotationOf.valued<Qualifier, Any> { it.value }),
            eventListenerAnnotations = listOf(AnnotationOf.tagged<EventListener>()),
            beanAnnotations = listOf(AnnotationOf.valued<Bean, _> { it.value }),
            orderAnnotations = listOf(AnnotationOf.valued<Order, Int> { it.value }),
        )
    }
}

/**
 * Reflects on a constructor to create a Component definition.
 *
 * @param T The type of the component
 * @param constructor The constructor to use for instantiation
 * @param name Optional name for the component
 * @param qualifiers Optional qualifiers for the component
 * @param primary Whether this component is the primary implementation
 * @return Component definition created from reflection
 */
inline fun <reified T: Any> Reflector.reflectConstructor(
    constructor: KFunction<T>,
    receiver: Any? = null,
    name: String = "",
    qualifiers: List<Any> = emptyList(),
    primary: Boolean = false,
    order: Int = Component.DEFAULT_ORDER
): Component<T> = reflectConstructor(constructor, TypeProxy<T>(), receiver, name, qualifiers, primary, order)

/**
 * Reflects on a class and constructor to create a Component definition.
 *
 * @param T The type of the component
 * @param type The type of the component
 * @param constructor The constructor to use for instantiation
 * @param name Optional name for the component
 * @param qualifiers Optional qualifiers for the component
 * @param primary Whether this component is the primary implementation
 * @return Component definition created from reflection
 */
fun <T: Any> Reflector.reflectConstructor(
    constructor: KFunction<T>,
    type: TypeProxy<T>,
    receiver: Any? = null,
    name: String = "",
    qualifiers: List<Any> = emptyList(),
    primary: Boolean = false,
    order: Int = Component.DEFAULT_ORDER
): Component<T> {
    check(!constructor.isSuspend)
    val klass = type.type.classifier as KClass<*>
    val construct: Context.(List<Any?>) -> T =
        if (receiver == null) { args -> construct(constructor, args) }
        else { args -> construct(constructor, listOf(receiver) + args) }

    val typeSubstitution = substitutionMap(klass, type.type)

    val constructorArgs = constructor.parameters.drop((receiver != null).compareTo(false)).map { p ->
        val paramType = substituteType(typeSubstitution, p.type)
        valueParser.dependencyFor(name, type.type, TypeProxy(paramType),
            qualifier=qualifierAnnotations.firstQualifier(p),
            required=autowiredAnnotations.annotation(p) ?: (!p.isOptional && !paramType.isMarkedNullable),
            valueExpression=valueAnnotations.annotation(p),
        )
    }

    val postConstruct = Box<Callback?>(null)
    val close = Box<Callback?>(null)
    val fields = ArrayList<Dependency>()
    val setters = ArrayList<Sink>()
    val listenerArgs = ArrayList<KType>()
    val listenerHandlers = ArrayList<Sink>()
    val superTypes = HashSet<KType>()

    if (maybeExtendsAutoClosable(klass))
        close.value = { (it as? AutoCloseable)?.close() }

    val queue = ArrayDeque<KType>().apply { add(type.type) }
    var typeSubstitutionOrNull = if (true) typeSubstitution else null
    val processedProperties = HashSet<String>()
    val processedListeners = HashSet<Pair<String, KType>>()
    val processedLifecycle = HashSet<String>()
    while (!queue.isEmpty()) {
        scanMembers(
            name, queue.removeFirst(), superTypes, fields, setters, listenerArgs, listenerHandlers, postConstruct, close,
            processedProperties, processedListeners, processedLifecycle,
            typeSubstitutionOrNull, queue,
        )
        typeSubstitutionOrNull = null
    }

    return Component<T>(
        type = type.type,
        name = name.ifEmpty { Component.defaultName(klass) },
        qualifiers = qualifiers,
        superTypes = superTypes,
        construct = construct,
        constructorArgs = constructorArgs,
        primary = primary,
        fields = fields.compact(),
        setters = setters.compact(),
        listenerArgs = listenerArgs,
        listenerHandlers = listenerHandlers.compact(),
        postConstruct = postConstruct.value,
        close = close.value,
        required = true,
        order = order,
    )
}

/**
 * Reflects on a class type to create a Component definition, automatically selecting the constructor.
 *
 * This method uses annotations to determine component name, primary status, and
 * which constructor to use. If multiple public constructors exist, it uses the one
 * marked with @Autowired.
 *
 * @param T The type of the component
 * @param type The type of the component
 * @return Component definition created from reflection
 */
fun <T: Any> Reflector.reflect(type: TypeProxy<T>): Component<T> {
    @Suppress("UNCHECKED_CAST")
    val klass = type.type.classifier as KClass<T>
    val name = (componentAnnotations.annotation(klass) ?: "").ifEmpty { Component.defaultName(klass) }
    val qualifiers = qualifierAnnotations.allQualifiers(klass).toList().let {
        if (name in it) it - listOf(name) else it
    }
    val primary = primaryAnnotations.annotated(klass)
    val orderValue = orderAnnotations.annotation(klass) ?: Component.DEFAULT_ORDER
    val cons1 = klass.constructors.filter { c -> c.visibility == KVisibility.PUBLIC }.ifEmpty {
        throw IllegalArgumentException("Missing public constructor for $klass")
    }
    val cons = if (cons1.size == 1) cons1 else cons1.filter(autowiredAnnotations::annotated)
    if (cons.size != 1)
        throw IllegalArgumentException("Ambiguous constructor")
    return reflectConstructor(cons.first(), type, name=name, qualifiers=qualifiers, primary=primary, order=orderValue)
}

/**
 * See [reflect]
 */
fun <T: Any> Reflector.reflect(klass: KClass<T>): Component<T> = reflect(classType(klass))

/**
 * Reflects on a type to create a Component definition, using type reification.
 *
 * This is a convenience method that uses type reification to avoid explicit type parameters.
 * It creates a Component definition for the specified type, automatically selecting
 * the constructor and processing annotations.
 *
 * @param T The type of the component to reflect
 * @return Component definition created from reflection
 * @throws IllegalArgumentException if the class has ambiguous constructors or other reflection issues
 * @see reflect
 */
inline fun <reified T: Any> Reflector.reflect(): Component<T> = reflect(TypeProxy<T>())

/**
 * Reflects on an object or class instance to find all @Bean methods and create Component definitions.
 *
 * This method scans the object or class for methods annotated with @Bean and creates a Component
 * definition for each one. The component type is the return type of the factory method.
 *
 * The @Bean annotation works similarly to Spring's @Bean, allowing you to define components
 * programmatically in configuration objects:
 *
 * ```kotlin
 * object AppConfig {
 *     @Bean
 *     fun dataSource(): DataSource = BasicDataSource().apply {
 *         url = "jdbc:h2:mem:test"
 *         username = "sa"
 *     }
 *
 *     @Bean
 *     fun userRepository(dataSource: DataSource): UserRepository =
 *         JdbcUserRepository(dataSource)
 *
 *     @Bean("auditService")
 *     @Qualifier("production")
 *     @Primary
 *     fun createAuditService(): AuditService =
 *         ProductionAuditService()
 * }
 *
 * // Get all bean components
 * val components = Reflector.Default.reflectFactory(AppConfig)
 * ```
 *
 * Key features:
 * - Method parameters are treated as dependencies and will be autowired
 * - @Bean methods can be customized with @Qualifier, @Primary, etc.
 * - Custom bean names can be specified in the @Bean annotation
 *
 * **Important difference from Spring**: Unlike Spring's @Bean methods, mindi's implementation does
 * not use runtime proxies to cache bean instances. In Spring, when one @Bean method calls another
 * @Bean method internally, the container intercepts the call and returns the cached instance. In
 * mindi, each @Bean method call creates a new instance. To share instances between beans, inject
 * them as dependencies instead of calling @Bean methods directly.
 *
 * @param T The type of the factory object
 * @param factory The object instance containing @Bean factory methods
 * @return List of Component definitions created from the factory methods
 */
inline fun <reified T: Any> Reflector.reflectFactory(factory: T): List<Component<*>> =
    reflectFactory(factory, TypeProxy<T>())


/**
 * Reflects on a class type to find all @Bean methods and create Component definitions.
 *
 * This is a convenience method that uses type reification to avoid explicit type parameters.
 * It attempts to create an instance of the provided class type using a no-argument constructor
 * and then scans for @Bean methods on that instance.
 *
 * @param T The type of the class containing @Bean methods
 * @return List of Component definitions created from the factory methods
 * @throws IllegalArgumentException if the class doesn't have a public no-argument constructor
 * @see reflectFactory
 */
inline fun <reified T: Any> Reflector.reflectFactory(): List<Component<*>> =
    reflectFactory(TypeProxy<T>())

/**
 * Reflects on a class type to find all @Bean methods and create Component definitions.
 * 
 * This method instantiates the given type using its no-argument constructor and then
 * scans the instance for methods annotated with @Bean to create Component definitions.
 *
 * @param T The type of the class containing @Bean methods
 * @param type TypeProxy representing the factory class
 * @return List of Component definitions created from the factory methods
 * @throws IllegalArgumentException if the class doesn't have a public no-argument constructor
 * @see reflectFactory
 */
fun <T: Any> Reflector.reflectFactory(type: TypeProxy<T>): List<Component<*>> {
    val klass = type.klass
    val cons = klass.constructors.filter { it.visibility == KVisibility.PUBLIC && it.parameters.all { it.isOptional } }
    require(cons.size == 1) {
        if (cons.isEmpty())
            "$type does not have a public no argument constructor"
        else
            "$type has multiple no argument constructors"
    }
    return reflectFactory(cons.first().callBy(emptyMap()) cast type, type)
}

/**
 * Reflects on a class to find all @Bean methods and create Component definitions.
 *
 * Convenience method that takes a KClass parameter instead of a TypeProxy.
 *
 * @param klass The class to scan for @Bean factory methods
 * @return List of Component definitions created from the factory methods
 * @throws IllegalArgumentException if the class doesn't have a public no-argument constructor
 * @see reflectFactory
 */
fun Reflector.reflectFactory(klass: KClass<*>): List<Component<*>> =
    reflectFactory(classType(klass))

/**
 * Reflects on an object instance to find all @Bean methods and create Component definitions.
 *
 * This method takes both an object instance and its type representation, and scans for methods
 * annotated with @Bean to create Component definitions. The type information is used to 
 * properly handle generic type parameters.
 *
 * @param T The type of the factory object
 * @param factory The object instance containing @Bean factory methods
 * @param type TypeProxy representing the factory object's type
 * @return List of Component definitions created from the factory methods
 */
fun <T: Any> Reflector.reflectFactory(factory: T, type: TypeProxy<T>): List<Component<*>> {
    val klass = type.type.classifier as KClass<*>
    val subst = substitutionMap(klass, type.type)
    return klass.members.mapNotNull next@{ member ->
        if (member !is KFunction<*> || member.isSuspend) return@next null
        val name = beanAnnotations.annotation(member) ?: return@next null
        val orderValue = orderAnnotations.annotation(member) ?: Component.DEFAULT_ORDER
        @Suppress("UNCHECKED_CAST")
        reflectConstructor(
            member as KFunction<Any>,
            TypeProxy<Any>(substituteType(subst, member.returnType)),
            receiver = factory,
            name = name.ifEmpty { member.name.replaceFirstChar { it.lowercase() } },
            qualifiers = qualifierAnnotations.allQualifiers(member).toList(),
            primary = primaryAnnotations.annotated(member),
            order = orderValue,
        )
    }
}

/**
 * Scans a class's members for special annotations and builds injection metadata.
 *
 * This method recursively processes class members including those from parent classes.
 * It tracks already processed methods across the entire class hierarchy to prevent
 * duplicate handlers for overridden methods.
 */
private fun Reflector.scanMembers(
    componentName: String,
    type: KType,
    superTypes: MutableSet<KType>,
    fields: MutableList<Dependency>,
    setters: MutableList<Sink>,
    listenerArgs: MutableList<KType>,
    listenerHandlers: MutableList<Sink>,
    postConstruct: Box<Callback?>,
    close: Box<Callback?>,
    processedProperties: MutableSet<String>,
    processedListeners: MutableSet<Pair<String, KType>>,
    processedLifecycle: MutableSet<String>,
    typeSubstitutionOrNull: Map<String, KTypeProjection>?,
    queue: ArrayDeque<KType>,
) {
    val klass = type.classifier as KClass<*>
    if (type in superTypes)
        return
    superTypes.add(type)
    if (type.arguments.isNotEmpty())
        superTypes.add(klass.starProjectedType)
    if (klass == Any::class || (klass.qualifiedName ?: "").startsWith("kotlin.Function"))
        return

    val typeSubstitution = typeSubstitutionOrNull ?: substitutionMap(klass, type)

    val postConstructCbs = mutableListOf<Callback>()
    val closeCbs = mutableListOf<Callback>()
    for (m in klass.declaredMembers) {
        if (m.isSuspend) continue

        val params = m.parameters
        val isPrivate = m.visibility == KVisibility.PRIVATE

        // Process event listener methods
        if (eventListenerAnnotations.annotated(m) && params.size == 2 &&
            params[0].kind == KParameter.Kind.INSTANCE
        ) {
            val eventType = substituteType(typeSubstitution, params[1].type)
            // Skip if not private and already processed a listener with same name and parameter type
            val listenerKey = m.name to eventType
            if (!isPrivate && listenerKey in processedListeners)
                continue

            if (!isPrivate)
                processedListeners.add(listenerKey)

            m.setAccessible()
            listenerArgs.add(eventType)
            listenerHandlers.add(m::call)
            continue
        }

        // Process lifecycle methods
        if (params.size == 1 && params[0].kind == KParameter.Kind.INSTANCE) {
            if (postConstructAnnotations.annotated(m) && (isPrivate || m.name !in processedLifecycle)) {
                if (!isPrivate)
                    processedLifecycle.add(m.name)
                m.setAccessible()
                postConstructCbs.add(m::call)
            } else if (preDestroyAnnotations.annotated(m) && (isPrivate || m.name !in processedLifecycle)) {
                if (!isPrivate)
                    processedLifecycle.add(m.name)
                m.setAccessible()
                closeCbs.add(m::call)
            }
        }

        val javaField: KAnnotatedElement? = (m as? KProperty<*>)?.javaField?.let {
            object : KAnnotatedElement { override val annotations = it.annotations.toList() }
        }

        // Process injectable fields and setters
        val required = autowiredAnnotations.annotation(m) ?: javaField?.let(autowiredAnnotations::annotation)
        val valueExpression = if (required != null) null else valueAnnotations.annotation(m) ?: javaField?.let(valueAnnotations::annotation)
        val qualifier =
            if (required == null) null
            else qualifierAnnotations.annotation(m) ?: javaField?.let(qualifierAnnotations::annotation)
        if (required == null && valueExpression == null)
            continue

        val isRequired = required != false
        if (m is KMutableProperty1<*, *>) {
            if (!isPrivate && m.name in processedProperties)
                continue // Skip this property as we've already processed it

            if (!isPrivate)
                processedProperties.add(m.name)

            val setter = m.setter
            m.setAccessible()
            setter.setAccessible()
            val fieldType = substituteType(typeSubstitution, m.returnType)
            fields.add(valueParser.dependencyFor(componentName, type, TypeProxy(fieldType), qualifier, isRequired, valueExpression))
            if (!fieldType.isMarkedNullable)
                setters.add { o, v -> v?.let { setter.call(o, it) } }
            else
                setters.add { o, v -> setter.call(o, v) }
        } else if (params.size == 2 && params[0].kind == KParameter.Kind.INSTANCE && m.name.startsWith("set")) {
            // Extract the property name from setter (e.g., setFoo -> foo)
            val propertyName = m.name.substring(3).replaceFirstChar { it.lowercase() }

            if (!isPrivate && propertyName in processedProperties)
                continue // Skip this setter as we've already processed it for this property

            if (!isPrivate)
                processedProperties.add(propertyName)

            val fieldType = substituteType(typeSubstitution, params[1].type)

            m.setAccessible()
            fields.add(valueParser.dependencyFor(componentName, type, TypeProxy(fieldType), qualifier, isRequired, valueExpression))

            val p1 = params[1]
            if (p1.isOptional && required == false)
                setters.add { o, v -> if (v == null) m.callBy(mapOf(m.parameters[0] to o)) else m.call(o, v) }
            else if (!fieldType.isMarkedNullable)
                setters.add { o, v -> v?.let { m.call(o, it) } }
            else
                setters.add(m::call)
        }
    }

    // Combine callbacks
    if (postConstructCbs.isNotEmpty())
        postConstruct.value = compose(postConstructCbs.foldRight(null, ::compose), postConstruct.value)

    if (closeCbs.isNotEmpty())
        close.value = compose(closeCbs.foldRight(null, ::compose), close.value)

    for (parent in klass.supertypes)
        queue.add(substituteType(typeSubstitution, parent))
}

private fun <T: Any> ValueResolver.dependencyFor(componentName: String, componentType: KType, depType: TypeProxy<T>, qualifier: Any?, required: Boolean, valueExpression: String?) =
    if (valueExpression != null)
        Dependency.parseValueExpressionFor(depType, valueExpression, componentName, componentType, this)
    else
        Dependency(depType.type, qualifier, required)

private fun substitutionMap(klass: KClass<*>, type: KType): Map<String, KTypeProjection> =
    klass.typeParameters.map { it.name }.zip(type.arguments).toMap()

private fun substituteTypeIf(typeSubstitution: Map<String, KTypeProjection>, type: KType): KType? {
    if (typeSubstitution.isEmpty()) return null
    val args = type.arguments
    if (args.isEmpty()) {
        val klass = type.classifier
        if (klass is KTypeParameter)
            return typeSubstitution[klass.name]!!.type!!
    }
    val substitutedArgs = args.map { arg -> substituteType(typeSubstitution, arg) }
    if (substitutedArgs.zip(args).all { (old, new) -> old === new })
        return null
    return (type.classifier as KClass<*>).createType(substitutedArgs, type.isMarkedNullable, type.annotations)
}

private fun substituteType(typeSubstitution: Map<String, KTypeProjection>, type: KType): KType =
    substituteTypeIf(typeSubstitution, type) ?: type

private fun substituteType(typeSubstitution: Map<String, KTypeProjection>, arg: KTypeProjection): KTypeProjection {
    val argType = arg.type ?: return KTypeProjection.STAR
    val klass = argType.classifier
    val substituted = if (klass is KTypeParameter) {
        val subst = typeSubstitution[klass.name] ?: throw IllegalArgumentException("Unbound type variable ${klass.name}")
        if (subst.variance != KVariance.INVARIANT) return subst
        subst.type ?: typeOf<Any?>() // FIXME: use proper upper bound(s)
    } else {
        substituteTypeIf(typeSubstitution, argType) ?: return arg
    }
    return when (arg.variance!!) {
        KVariance.INVARIANT -> KTypeProjection.invariant(substituted)
        KVariance.IN -> KTypeProjection.contravariant(substituted)
        KVariance.OUT -> KTypeProjection.covariant(substituted)
    }
}

internal class Box<T>(var value: T)

private fun <T> construct(con: KFunction<T>, args: List<Any?>): T {
    if (null !in args) return con.call(*args.toTypedArray())
    val params = con.parameters
    require(args.size == params.size) { "expected exactly ${params.size} arguments, but got ${args.size}" }
    return con.callBy(params.zip(args).filter { (param, arg) -> arg != null || !param.isOptional }.toMap())
}

/**
 * Creates a TypeProxy for a class, ensuring it has no unbound type parameters.
 */
internal fun <T: Any> classType(klass: KClass<T>): TypeProxy<T> {
    require(klass.typeParameters.isEmpty()) { "cannot reflect on class type with unbound type parameters, got $klass" }
    return TypeProxy<T>(klass.starProjectedType)
}

