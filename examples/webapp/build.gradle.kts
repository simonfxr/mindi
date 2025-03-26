plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.sfxr.examples"
version = "0.1.0"

dependencies {
    // Use local mindi dependency
    implementation("de.sfxr:mindi:0.1.0")

    // Ktor for web server
    val ktorVersion = "2.3.9"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // Log4j2 for logging
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.22.1")

    // Test dependencies
    testImplementation(kotlin("test-junit"))
}

application {
    mainClass.set("de.sfxr.examples.webapp.ApplicationKt")
}

// Configure the shadow JAR
tasks.shadowJar {
    archiveBaseName.set("mindi-webapp-example")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    mergeServiceFiles()
    manifest {
        attributes(mapOf(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        ))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}