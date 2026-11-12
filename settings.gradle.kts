@file:Suppress("UnstableApiUsage")  // Gradle still marks dependencyResolutionManagement as incubating

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

include("courses-platform", "courses-domain", "courses-write", "courses-read", "courses-api", "courses-frontend", "courses-test-support")

includeBuild("build-logic")
