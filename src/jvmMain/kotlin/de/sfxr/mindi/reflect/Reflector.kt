package de.sfxr.mindi.reflect

import de.sfxr.mindi.*
import de.sfxr.mindi.annotations.*
import de.sfxr.mindi.internal.compact
import de.sfxr.mindi.internal.compose
import kotlin.reflect.*
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.starProjectedType
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
 */
data class Reflector(
    val autowiredAnnotations: List<AnnotationOf<Boolean>>,
    val componentAnnotations: List<AnnotationOf<String>>,
    val postConstructAnnotations: List<TagAnnotation>,
    val preDestroyAnnotations: List<TagAnnotation>,
    val primaryAnnotations: List<TagAnnotation>,
    val valueAnnotations: List<AnnotationOf<String>>,
    val qualifierAnnotations: List<AnnotationOf<String>>,
    val eventListenerAnnotations: List<TagAnnotation>,
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
            qualifierAnnotations = listOf(AnnotationOf.valued<Qualifier, _> { it.value }),
            eventListenerAnnotations = listOf(AnnotationOf.tagged<EventListener>()),
        )
    }
}

/**
 * Reflects on a constructor to create a Component definition.
 *
 * @param T The type of the component
 * @param constructor The constructor to use for instantiation
 * @param names Optional names/qualifiers for the component
 * @param primary Whether this component is the primary implementation
 * @return Component definition created from reflection
 */
inline fun <reified T: Any> Reflector.reflectConstructor(constructor: KFunction<T>, names: List<String> = emptyList(), primary: Boolean = false): Component<T> =
    reflectConstructor(constructor, TypeProxy<T>(), names, primary)

/**
 * Reflects on a class and constructor to create a Component definition.
 *
 * @param T The type of the component
 * @param type The type of the component
 * @param constructor The constructor to use for instantiation
 * @param names Optional names/qualifiers for the component
 * @param primary Whether this component is the primary implementation
 * @return Component definition created from reflection
 */
fun <T: Any> Reflector.reflectConstructor(constructor: KFunction<T>, type: TypeProxy<T>, names: List<String> = emptyList(), primary: Boolean = false): Component<T> {
    check(!constructor.isSuspend)
    val klass = type.type.classifier as KClass<*>
    val construct: Context.(List<Any?>) -> T = { args -> construct(constructor, args) }
    val constructorArgs: List<Dependency> = constructor.parameters.map { p ->
        val valueExpression = p.findFirstAnnotation(valueAnnotations)
        if (valueExpression != null) {
            Dependency.parseValueExpression(TypeProxy(p.type), valueExpression)
        } else {
            val autowiredRequired = p.findFirstAnnotation(autowiredAnnotations)
            val required = autowiredRequired ?: (!p.isOptional && !p.type.isMarkedNullable)
            Dependency(p.type, qualifier = p.findFirstAnnotation(qualifierAnnotations), required=required)
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
        defaultName = Component.defaultName(klass),
        names = names,
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
 * @param klass The class of the component
 * @return Component definition created from reflection
 */
fun <T: Any> Reflector.reflect(type: TypeProxy<T>): Component<T> {
    val klass = type.type.classifier as KClass<*>
    val name = (klass.findFirstAnnotation(componentAnnotations) ?: "").ifEmpty { Component.defaultName(klass) }
    val qualifiers = HashSet(klass.findAllAnnotations(qualifierAnnotations)).apply {
        remove(name)
    }
    val names = listOf(name) + qualifiers.toList()
    val primary = klass.hasAnyAnnotation(primaryAnnotations)
    val cons1 = klass.constructors.filter { c -> c.visibility == KVisibility.PUBLIC }
    val cons = if (cons1.size <= 1) cons1 else
        cons1.filter { c -> c.hasAnyAnnotation(autowiredAnnotations) }
    @Suppress("UNCHECKED_CAST")
    val constructor = when {
        cons.isEmpty() && cons1.isEmpty() -> throw IllegalArgumentException("Missing public constructor")
        cons.size > 1 -> throw IllegalArgumentException("Ambiguous constructor")
        else -> cons.first() as KFunction<T>
    }
    return reflectConstructor(constructor, type, names=names, primary=primary)
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
 * Scans a class's members for special annotations and builds injection metadata.
 */
private fun Reflector.scanMembers(
    type: KType,
    superTypes: MutableSet<KType>,
    fields: MutableList<Dependency>,
    setters: MutableList<Sink>,
    listenerArgs: MutableList<KType>,
    listenerHandlers: MutableList<Sink>,
    postConstruct: Box<Callback?>,
    close: Box<Callback?>
) {
    val klass = type.classifier as KClass<*>
    if (type in superTypes)
        return
    superTypes.add(type)
    if (klass == Any::class || (klass.qualifiedName ?: "").startsWith("kotlin.Function"))
        return
    val postConstructCbs = mutableListOf<Callback>()
    val closeCbs = mutableListOf<Callback>()
    for (m in klass.declaredMembers) {
        if (m.isSuspend || m.isAbstract)
            continue
        val params = m.parameters
        val param1Type = params.getOrNull(1)?.type
        val param1Klass = param1Type?.classifier

        // Process event listener methods
        if (m.hasAnyAnnotation(eventListenerAnnotations) && params.size == 2 &&
            params[0].kind == KParameter.Kind.INSTANCE && param1Klass is KClass<*>
        ) {
            if (m.visibility != KVisibility.PUBLIC)
                runCatching { m.setAccessible() }
            listenerArgs.add(param1Klass.starProjectedType)
            listenerHandlers.add { o, v -> m.call(o, v) }
            continue
        }

        // Process lifecycle methods
        if (params.size == 1 && params[0].kind == KParameter.Kind.INSTANCE && m.visibility == KVisibility.PUBLIC) {
            if (m.hasAnyAnnotation(postConstructAnnotations))
                postConstructCbs.add { m.call(it) }
            if (m.hasAnyAnnotation(preDestroyAnnotations))
                closeCbs.add { m.call(it) }
        }

        // Process injectable fields and setters
        val autowiredRequired = m.findFirstAnnotation(autowiredAnnotations)
        val valueExpression = m.takeIf { autowiredRequired == null }?.findFirstAnnotation(valueAnnotations)
        val qualifier = m.takeIf { autowiredRequired != null }?.findFirstAnnotation(qualifierAnnotations)
        if (autowiredRequired != null || valueExpression != null) {
            if (m is KMutableProperty1<*, *>) {
                val setter = m.setter
                if (m.visibility != KVisibility.PUBLIC)
                    runCatching { m.setAccessible() }
                val fieldType = m.returnType
                if (valueExpression != null)
                    fields.add(Dependency.parseValueExpression(TypeProxy(fieldType), valueExpression))
                else
                    fields.add(Dependency(fieldType, qualifier, autowiredRequired!!))
                if (!fieldType.isMarkedNullable)
                    setters.add { o, v -> v?.let { setter.call(o, it) } }
                else
                    setters.add { o, v -> setter.call(o, v) }
            } else if (params.size == 2 && params[0].kind == KParameter.Kind.INSTANCE && param1Klass is KClass<*> && m.name.startsWith("set")) {
                if (m.visibility != KVisibility.PUBLIC)
                    runCatching { m.setAccessible() }
                if (valueExpression != null)
                    fields.add(Dependency.parseValueExpression(TypeProxy(param1Type), valueExpression))
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
        scanMembers(parent, superTypes, fields, setters, listenerArgs, listenerHandlers, postConstruct, close)
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
