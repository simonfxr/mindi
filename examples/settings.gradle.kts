plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mindi-examples"

// Include the webapp subproject
include(":webapp")

// Include the mindi root project
includeBuild("../") {
    dependencySubstitution {
        substitute(module("de.sfxr:mindi")).using(project(":"))
    }
}
