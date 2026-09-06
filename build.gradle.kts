@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

atomicfu {
    transformJvm = true // Disable JVM transformation, only transform native code
    jvmVariant = "FU"
}

group = "de.sfxr"

val forcedVersion = System.getenv("FORCED_VERSION")?.takeIf { it.isNotBlank() }
version = forcedVersion ?: "0.1.0"

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

    js(IR) {
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
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                implementation(libs.atomicfu)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nativeMain by getting {}
        val nativeTest by getting {}

        // POSIX-specific code
        val posixMain by creating {
            dependsOn(nativeMain)
        }
        val posixTest by creating {
            dependsOn(nativeTest)
        }

        // Windows-specific code
        val windowsMain by creating {
            dependsOn(nativeMain)
        }
        val windowsTest by creating {
            dependsOn(nativeTest)
        }

        // Configure platform-specific source sets
        val linuxX64Main by getting {
            dependsOn(posixMain)
        }
        val linuxArm64Main by getting {
            dependsOn(posixMain)
        }
        val mingwX64Main by getting {
            dependsOn(windowsMain)
        }

        // Test source sets
        val linuxX64Test by getting {
            dependsOn(posixTest)
        }
        val linuxArm64Test by getting {
            dependsOn(posixTest)
        }
        val mingwX64Test by getting {
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
