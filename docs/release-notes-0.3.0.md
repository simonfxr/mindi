# mindi 0.3.0

## Fixes

- Missing optional qualified dependencies now resolve to null; ambiguous providers still fail.
- Non-null providers satisfy nullable dependencies of the same shape, and reflection preserves nullable type parameters.
- Event listener caching distinguishes runtime event classes published through the same declared type.
- Startup and shutdown attempt all registered cleanup callbacks, retain suppressed failures, and handle errors as well as exceptions.
- Context closure during construction, initialization, or refresh prevents startup from returning a closed context and cleans up constructed instances.
- JVM reflection supports public members of non-public classes and honors custom qualifiers on injection points.
- JVM scanning closes resource streams, preserves literal plus signs in JAR paths, and handles loader-exposed nested JARs and `BOOT-INF/classes` resources.

## Compatibility notes

- Reflected `@PreDestroy` callbacks now run from subclass to superclass, followed by automatic `AutoCloseable.close()`. An annotated public `close()` is invoked exactly once, at the end of cleanup.
- Reflected injection points reject multiple distinct qualifiers instead of selecting one arbitrarily. Repeating the same qualifier across annotation sites is allowed; providers may still expose multiple qualifiers.
- Cleanup callbacks after a failing callback now run. Ensure each callback releases its own resources reliably.

## Documentation

The README clarifies platform support, static graph validation, scanning limits, automatic resource management, and exact generic type matching. Generic variance and nested nullability conversions are not inferred automatically.

## Usage

```kotlin
repositories { mavenCentral() }
dependencies { implementation("de.sfxr:mindi:0.3.0") }
```

Plain Maven/JVM consumers use `de.sfxr:mindi-jvm:0.3.0`.
