package de.sfxr.examples.webapp

import de.sfxr.mindi.annotations.Component
import de.sfxr.mindi.annotations.Value

/**
 * Server configuration component that loads values from application.properties
 * and exposes them as typed Java properties.
 */
@Component
class ServerConfig(
    @Value("\${server.port:8080}")
    val port: Int,

    @Value("\${server.host:0.0.0.0}")
    val host: String,
)