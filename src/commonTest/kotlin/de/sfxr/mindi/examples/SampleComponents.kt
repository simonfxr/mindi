package de.sfxr.mindi.examples

import de.sfxr.mindi.annotations.*

@Component("a_foo")
class Foo {
    @PostConstruct
    fun afterPropertiesSet() { println("Foo()") }

    @PreDestroy
    fun dispose() { println("~Foo()") }
}

class Bar(val foo: Foo) {
    @PostConstruct
    fun afterPropertiesSet() { println("Bar") }
}

class Baz(val factor: Double): AutoCloseable {
    @Autowired
    private lateinit var foo: Foo

    @EventListener
    fun onFoo(x: String) {
        println("handle $x")
    }

    override fun close() {
        println("~Baz")
    }
}

fun newBaz(@Value("\${bazfactor:3.14}") factor: Double): Baz = Baz(factor.also { println("factor:$it") })