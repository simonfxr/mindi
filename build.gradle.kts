@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.nexus.publish)
    `maven-publish`
    id("signing")
}

atomicfu {
    transformJvm = false // Disable JVM transformation, only transform native code
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
        compilations.getByName("main") {
            kotlinOptions {
                jvmTarget = "11"
            }
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
                connection.set("scm:git:git://github.com/simonfxr/mindi.git")
                developerConnection.set("scm:git:ssh://github.com/simonfxr/mindi.git")
                url.set("https://github.com/simonfxr/mindi")
            }
        }
    }

    // Configure repositories
    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = project.findProperty("mavenCentralUsername") as String? ?: System.getenv("mavenCentralUsername")
                password = project.findProperty("mavenCentralPassword") as String? ?: System.getenv("mavenCentralPassword")
            }
        }
    }
}

// Signing configuration
signing {
    val signingKey: String? = project.findProperty("signingKey") as String? ?: System.getenv("signingKey")
    val signingPassword: String? = project.findProperty("signingPassword") as String? ?: System.getenv("signingPassword")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Sonatype Nexus publishing configuration
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(project.findProperty("mavenCentralUsername") as String? ?: System.getenv("mavenCentralUsername"))
            password.set(project.findProperty("mavenCentralPassword") as String? ?: System.getenv("mavenCentralPassword"))
        }
    }
}
