@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

atomicfu {
    transformJvm = true
    jvmVariant = "FU"
}

group = "de.sfxr"

val forcedVersion = providers.environmentVariable("FORCED_VERSION").orNull?.takeIf { it.isNotBlank() }
version = forcedVersion ?: providers.gradleProperty("version").get()

println("Building with version: $version")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    js {
        nodejs()
        browser {
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    // POSIX targets
    linuxX64()
    linuxArm64()

    // Windows targets
    mingwX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                implementation(libs.atomicfu)
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nativeMain = getByName("nativeMain")
        val nativeTest = getByName("nativeTest")

        // POSIX-specific code
        val posixMain = create("posixMain") {
            dependsOn(nativeMain)
        }
        val posixTest = create("posixTest") {
            dependsOn(nativeTest)
        }

        // Windows-specific code
        val windowsMain = create("windowsMain") {
            dependsOn(nativeMain)
        }
        val windowsTest = create("windowsTest") {
            dependsOn(nativeTest)
        }

        // Configure platform-specific source sets
        getByName("linuxX64Main") {
            dependsOn(posixMain)
        }
        getByName("linuxArm64Main") {
            dependsOn(posixMain)
        }
        getByName("mingwX64Main") {
            dependsOn(windowsMain)
        }

        // Test source sets
        getByName("linuxX64Test") {
            dependsOn(posixTest)
        }
        getByName("linuxArm64Test") {
            dependsOn(posixTest)
        }
        getByName("mingwX64Test") {
            dependsOn(windowsTest)
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        // Provide information required by Maven Central
        pom {
            name.set("mindi")
            description.set("Minimal Dependency Injection for Kotlin Multiplatform")
            url.set("https://github.com/simonfxr/mindi")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("simonfxr")
                    name.set("Simon Reiser")
                    email.set("me@sfxr.de")
                }
            }

            scm {
                connection.set("scm:git:https://github.com/simonfxr/mindi.git")
                developerConnection.set("scm:git:ssh://git@github.com/simonfxr/mindi.git")
                url.set("https://github.com/simonfxr/mindi")
            }
        }
    }

    // A disposable repository for inspecting the complete publication without uploading.
    repositories {
        maven {
            name = "Local"
            setUrl(layout.buildDirectory.dir("local-maven"))
        }
    }
}

mavenPublishing {
    // Upload for validation first; releasing publicly remains an explicit action.
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()
}
