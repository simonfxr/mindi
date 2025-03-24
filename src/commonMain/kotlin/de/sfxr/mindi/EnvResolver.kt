package de.sfxr.mindi

/**
 * Default ValueResolver that reads values from environment variables.
 *
 * This resolver converts dot notation to underscore notation to be
 * compatible with standard environment variable naming conventions.
 * For example, "app.config.port" becomes "app_config_port".
 */
object EnvResolver: ValueResolver {
    override fun resolve(key: String): String? = getenv(key.replace('.', '_'))
}
