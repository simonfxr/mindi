plugins {
    kotlin("jvm") version "2.4.21" apply false
}

group = "de.sfxr.examples"
version = "0.3.1"

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
