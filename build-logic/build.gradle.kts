plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
}

dependencies {
    // Exposes the generated catalog accessors to the precompiled convention scripts.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}

spotless {
    kotlin {
        // Generated Kotlin DSL accessors live under build/ and must not be linted.
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
