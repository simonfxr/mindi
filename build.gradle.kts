plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.atomicfu)
}

atomicfu {
    transformJvm = false // Disable JVM transformation, only transform native code
}

group = "de.sfxr"
version = "1.0-SNAPSHOT"

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
        // Browser target is configured but browser tests are explicitly disabled
        // because they require a browser to be installed
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
