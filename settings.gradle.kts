pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Auto-provision the JDK 21 toolchain on machines that only ship a newer JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "axon5-dcb-showcase"
include("platform", "domain", "write", "read", "api", "frontend")
