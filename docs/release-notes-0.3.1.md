# mindi 0.3.1

## Java 8 support

- The JVM library now supports Java 8+, including the functional API, annotation reflection, and component scanning.
- Compile against JDK 8 APIs and publish Java 8 bytecode and Gradle compatibility metadata.
- Replace newer Java APIs and accommodate Java 8 ZIP-filesystem behavior when scanning standard and nested JARs.
- Run the JVM test suite on Java 8 and Java 21 in CI.

Building mindi still uses JDK 21 to run Gradle, which provisions the JDK 8 compilation toolchain. The Ktor webapp example retains its separate Java 11+ baseline.

## Dependency updates

- Kotlin: 2.4.10 → 2.4.21.
- Dokka: 2.2.0 → 2.3.0.
- Example Ktor: 3.5.2 → 3.6.1.
- Refresh pinned CI actions.

## Usage

```kotlin
repositories { mavenCentral() }
dependencies { implementation("de.sfxr:mindi:0.3.1") }
```

Plain Maven/JVM consumers use `de.sfxr:mindi-jvm:0.3.1`.
