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
    primary: Boolean = false
): Component<T> = reflectConstructor(constructor, TypeProxy<T>(), receiver, name, qualifiers, primary)

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
    primary: Boolean = false
): Component<T> {
    check(!constructor.isSuspend)
    val klass = type.type.classifier as KClass<*>
    val construct: Context.(List<Any?>) -> T =
        if (receiver == null) { args -> construct(constructor, args) }
        else { args -> construct(constructor, listOf(receiver) + args) }
    val constructorArgs: List<Dependency> = constructor.parameters.drop((receiver != null).compareTo(false)).map { p ->
        val valueExpression = p.findFirstAnnotation(valueAnnotations)
        if (valueExpression != null) {
            Dependency.parseValueExpressionFor(TypeProxy(p.type), valueExpression, name, type.type, valueParser)
        } else {
            val autowiredRequired = p.findFirstAnnotation(autowiredAnnotations)
            val required = autowiredRequired ?: (!p.isOptional && !p.type.isMarkedNullable)
            Dependency(p.type, qualifier=p.findFirstQualifierAnnotations(qualifierAnnotations), required=required)
        }
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

    scanMembers(type.type, superTypes, fields, setters, listenerArgs, listenerHandlers, postConstruct, close)

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
    val klass = type.type.classifier as KClass<*>
    val name = (klass.findFirstAnnotation(componentAnnotations) ?: "").ifEmpty { Component.defaultName(klass) }
    val qualifiers = klass.findAllQualifierAnnotations(qualifierAnnotations).toList().let {
        if (name in it) it - listOf(name) else it
    }
    val primary = klass.hasAnyAnnotation(primaryAnnotations)
    val cons1 = klass.constructors.filter { c -> c.visibility == KVisibility.PUBLIC }
    val cons = if (cons1.size <= 1) cons1 else
        cons1.filter { c -> c.hasAnyAnnotation(autowiredAnnotations) }
    @Suppress("UNCHECKED_CAST")
    val constructor = when {
        cons.isEmpty() && cons1.isEmpty() -> throw IllegalArgumentException("Missing public constructor for $klass")
        cons.size > 1 -> throw IllegalArgumentException("Ambiguous constructor")
        else -> cons.first() as KFunction<T>
    }
    return reflectConstructor(constructor, type, name=name, qualifiers=qualifiers, primary=primary)
}

/**
 * See [reflect]
 */
fun <T: Any> Reflector.reflect(klass: KClass<T>): Component<T> {
    val type = klass.starProjectedType
    check(type.arguments.isEmpty()) { "cannot reflect on class type with unbound type parameters, got $type" }
    return reflect(TypeProxy<T>(type))
}

/**
 * See [reflect]
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
 * **Important difference from Spring**: Unlike Spring's @Bean methods, Mindi's implementation does
 * not use runtime proxies to cache bean instances. In Spring, when one @Bean method calls another
 * @Bean method internally, the container intercepts the call and returns the cached instance. In
 * Mindi, each @Bean method call creates a new instance. To share instances between beans, inject
 * them as dependencies instead of calling @Bean methods directly.
 *
 * @param factory The object or class instance containing @Bean factory methods
 * @return List of Component definitions created from the factory methods
 */
fun Reflector.reflectFactory(factory: Any): List<Component<*>> =
    factory::class.members.mapNotNull next@{ member ->
        if (member !is KFunction<*> || member.isSuspend) return@next null
        val name = member.findFirstAnnotation(beanAnnotations) ?: return@next null
        @Suppress("UNCHECKED_CAST")
        reflectConstructor(
            member as KFunction<Any>,
            TypeProxy<Any>(member.returnType),
            receiver = factory,
            name = name.ifEmpty { member.name.replaceFirstChar { it.lowercase() } },
            qualifiers = member.findAllQualifierAnnotations(qualifierAnnotations).toList(),
            primary = member.hasAnyAnnotation(primaryAnnotations),
        )
    }

/**
 * Scans a class's members for special annotations and builds injection metadata.
 *
 * This method recursively processes class members including those from parent classes.
 * It tracks already processed methods across the entire class hierarchy to prevent
 * duplicate handlers for overridden methods.
 */
private fun Reflector.scanMembers(
    type: KType,
    superTypes: MutableSet<KType>,
    fields: MutableList<Dependency>,
    setters: MutableList<Sink>,
    listenerArgs: MutableList<KType>,
    listenerHandlers: MutableList<Sink>,
    postConstruct: Box<Callback?>,
    close: Box<Callback?>,
    processedProperties: MutableSet<String> = HashSet(),
    processedListeners: MutableSet<Pair<String, KType>>  = HashSet(),
    processedLifecycle: MutableSet<String>  = HashSet(),
) {
    val klass = type.classifier as KClass<*>
    if (type in superTypes)
        return
    superTypes.add(type)
    if (type.arguments.isNotEmpty())
        superTypes.add(klass.starProjectedType)
    if (klass == Any::class || (klass.qualifiedName ?: "").startsWith("kotlin.Function"))
        return

    // Create type substitution map for generic types
    val typeSubstitution =
        klass.typeParameters.zip(type.arguments) { param, arg ->
            param.name to arg
        }.toMap()

    val postConstructCbs = mutableListOf<Callback>()
    val closeCbs = mutableListOf<Callback>()
    for (m in klass.declaredMembers) {
        if (m.isSuspend || m.isAbstract)
            continue
        val params = m.parameters
        val param1Type = params.getOrNull(1)?.type
        val param1Klass = param1Type?.classifier
        val isPrivate = m.visibility == KVisibility.PRIVATE

        // Process event listener methods
        if (m.hasAnyAnnotation(eventListenerAnnotations) && params.size == 2 &&
            params[0].kind == KParameter.Kind.INSTANCE && param1Klass is KClass<*>
        ) {
            // Skip if not private and already processed a listener with same name and parameter type
            val listenerKey = m.name to param1Type
            if (!isPrivate && listenerKey in processedListeners)
                continue

            if (!isPrivate)
                processedListeners.add(listenerKey)

            if (m.visibility != KVisibility.PUBLIC)
                runCatching { m.setAccessible() }
            listenerArgs.add(param1Type)
            listenerHandlers.add { o, v -> m.call(o, v) }
            continue
        }

        // Process lifecycle methods
        if (params.size == 1 && params[0].kind == KParameter.Kind.INSTANCE) {
            var added = false

            if (m.hasAnyAnnotation(postConstructAnnotations) && (isPrivate || m.name !in processedLifecycle)) {
                if (!isPrivate)
                    processedLifecycle.add(m.name)
                postConstructCbs.add { m.call(it) }
                added = true
            }

            if (m.hasAnyAnnotation(preDestroyAnnotations) && (isPrivate || m.name !in processedLifecycle)) {
                if (!isPrivate)
                    processedLifecycle.add(m.name)
                closeCbs.add { m.call(it) }
                added = true
            }

            if (added && m.visibility != KVisibility.PUBLIC)
                runCatching { m.setAccessible() }
        }

        val javaField: KAnnotatedElement? = (m as? KProperty<*>)?.javaField?.let {
            object : KAnnotatedElement {
                override val annotations = it.annotations.toList()
            }
        }

        // Process injectable fields and setters
        val autowiredRequired = m.findFirstAnnotation(autowiredAnnotations) ?: javaField?.findFirstAnnotation(autowiredAnnotations)
        val valueExpression = if (autowiredRequired != null) null else m.findFirstAnnotation(valueAnnotations) ?: javaField?.findFirstAnnotation(valueAnnotations)
        val qualifier =
            if (autowiredRequired == null) null
            else m.findFirstQualifierAnnotations(qualifierAnnotations) ?: javaField?.findFirstQualifierAnnotations(qualifierAnnotations)
        if (autowiredRequired != null || valueExpression != null) {
            val isPrivate = m.visibility == KVisibility.PRIVATE
            if (m is KMutableProperty1<*, *>) {
                if (!isPrivate && m.name in processedProperties)
                    continue // Skip this property as we've already processed it

                if (!isPrivate)
                    processedProperties.add(m.name)

                val setter = m.setter
                m.setAccessible()
                setter.setAccessible()
                val fieldType = m.returnType
                if (valueExpression != null)
                    fields.add(Dependency.parseValueExpressionFor(TypeProxy(fieldType), valueExpression, null, type, valueParser))
                else
                    fields.add(Dependency(fieldType, qualifier, autowiredRequired!!))
                if (!fieldType.isMarkedNullable)
                    setters.add { o, v -> v?.let { setter.call(o, it) } }
                else
                    setters.add { o, v -> setter.call(o, v) }
            } else if (params.size == 2 && params[0].kind == KParameter.Kind.INSTANCE && param1Klass is KClass<*> && m.name.startsWith("set")) {
                // Extract the property name from setter (e.g., setFoo -> foo)
                val propertyName = m.name.substring(3).replaceFirstChar { it.lowercase() }

                if (!isPrivate && propertyName in processedProperties)
                    continue // Skip this setter as we've already processed it for this property

                if (!isPrivate)
                    processedProperties.add(propertyName)

                if (m.visibility != KVisibility.PUBLIC)
                    runCatching { m.setAccessible() }
                if (valueExpression != null)
                    fields.add(Dependency.parseValueExpressionFor(TypeProxy(param1Type), valueExpression, null, type, valueParser))
                else
                    fields.add(Dependency(param1Type, qualifier, autowiredRequired!!))

                val p1 = params[1]
                if (p1.isOptional && autowiredRequired == false)
                    setters.add { o, v -> if (v == null) m.callBy(mapOf(m.parameters[0] to o)) else m.call(o, v) }
                else if (!param1Type.isMarkedNullable)
                    setters.add { o, v -> v?.let { m.call(o, it) } }
                else
                    setters.add { o, v -> m.call(o, v) }
            }
        }
    }

    // Combine callbacks
    if (postConstructCbs.isNotEmpty())
        postConstruct.value = compose(postConstructCbs.foldRight(null, ::compose), postConstruct.value)

    if (closeCbs.isNotEmpty())
        close.value = compose(closeCbs.foldRight(null, ::compose), close.value)

    // Process parent classes
    for (parent in klass.supertypes)
        scanMembers(
            substituteType(typeSubstitution, parent) ?: parent,
            superTypes, fields, setters, listenerArgs, listenerHandlers, postConstruct, close,
            processedProperties, processedListeners, processedLifecycle,
        )
}

/**
 * Substitutes type parameters in a KType with concrete types from a type substitution map.
 *
 * @param typeSubstitution Map from type parameter names to concrete types
 * @param type The type to apply substitutions to
 * @return A new KType with all type parameters replaced with their concrete types, or null if no substitutions were made
 */
private fun substituteType(typeSubstitution: Map<String, KTypeProjection>, type: KType): KType? {
    if (typeSubstitution.isEmpty()) return null
    val args = type.arguments
    val substitutedArgs = args.map { arg -> substituteTypeProjection(typeSubstitution, arg) }
    if (substitutedArgs == args)
        return null
    return (type.classifier as KClass<*>).createType(substitutedArgs, type.isMarkedNullable, type.annotations)
}

private fun substituteTypeProjection(typeSubstitution: Map<String, KTypeProjection>, arg: KTypeProjection): KTypeProjection {
    val argType = arg.type ?: return KTypeProjection.STAR
    val classifier = argType.classifier
    val substituted = if (classifier is KTypeParameter) {
        val subst = typeSubstitution[classifier.name]!!
        if (subst.variance != KVariance.INVARIANT)
            return subst
        subst.type!!
    } else {
        substituteType(typeSubstitution, argType)
    }
    return substituted?.let { replaced ->
        when (arg.variance!!) {
            KVariance.INVARIANT -> KTypeProjection.invariant(replaced)
            KVariance.IN -> KTypeProjection.contravariant(replaced)
            KVariance.OUT -> KTypeProjection.covariant(replaced)
        }
    } ?: arg
}

/**
 * A mutable box holding a single value.
 * Used to pass values by reference for modification in callbacks.
 */
internal class Box<T>(var value: T)

private fun <T> construct(con: KFunction<T>, args: List<Any?>): T {
    if (null !in args)
        return con.call(*args.toTypedArray())
    val params = con.parameters
    check(args.size == params.size) { "expected exactly ${params.size} arguments, but got ${args.size}" }
    val argMap = HashMap<KParameter, Any?>(args.size)
    for ((param, arg) in params.zip(args))
        if (arg != null || !param.isOptional)
            argMap[param] = arg
    return con.callBy(argMap)
}
