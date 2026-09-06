plugins {
    kotlin("jvm") version "2.4.10" apply false
}

group = "de.sfxr.examples"
version = "0.2.0"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
