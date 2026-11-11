plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "io.github.aanbrn.showcase"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "com.diffplug.spotless")

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                ktlint(libs.versions.ktlint.get())
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                ktlint(libs.versions.ktlint.get())
            }
        }
    }
}
