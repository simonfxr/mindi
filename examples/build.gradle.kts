plugins {
    kotlin("jvm") version "2.1.20" apply false
}

group = "de.sfxr.examples"
version = "0.1.0"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}