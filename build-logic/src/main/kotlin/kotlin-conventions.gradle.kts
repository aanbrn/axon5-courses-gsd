@file:Suppress("UnstableApiUsage") // jvm-test-suite APIs are still @Incubating

// Conventions for every Kotlin module: the JDK 21 toolchain, Spotless + ktlint, the `:courses-platform` BOM,
// and the test-suite naming rule (`*Tests`, `*CT`, `*IT`, `*E2E`) enforced by `checkTestNaming`.

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val javaVersion = libs.versions.java.get()
val ktlintVersion = libs.versions.ktlint.get()

kotlin {
    jvmToolchain(javaVersion.toInt())

    // Hibernate Validator reads type-use constraints such as List<@URL String> from the bytecode;
    // Kotlin omits JVM type annotations unless this flag is on.
    compilerOptions {
        freeCompilerArgs.add("-Xemit-jvm-type-annotations")
    }
}

// A suite runs only the classes whose name carries its suffix, so a misnamed test never runs silently.
val testSuffixes =
    mapOf(
        "test" to "Tests",
        "componentTest" to "CT",
        "integrationTest" to "IT",
        "e2eTest" to "E2E",
    )

testing {
    suites.withType<JvmTestSuite>().configureEach {
        val suffix = testSuffixes[name] ?: return@configureEach

        targets.all {
            testTask.configure {
                include("**/*$suffix.class")
            }
        }
    }
}

tasks.register("checkTestNaming") {
    group = "verification"
    description = "Fails when a test file does not carry the suffix of its suite."

    val suffixes = testSuffixes
    val root = projectDir

    doLast {
        val violations =
            suffixes.flatMap { (suite, suffix) ->
                val dir = root.resolve("src/$suite")
                if (!dir.isDirectory) return@flatMap emptyList()

                dir
                    .walkTopDown()
                    .filter { it.isFile && it.extension in setOf("kt", "java") }
                    .filterNot { it.nameWithoutExtension.endsWith(suffix) }
                    .map { "${it.relativeTo(root)} must end with '$suffix' ($suite suite)" }
                    .toList()
            }

        if (violations.isNotEmpty()) {
            throw GradleException("Test naming violations:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") { dependsOn("checkTestNaming") }

dependencies {
    implementation(platform(project(":courses-platform")))
}

spotless {
    kotlin {
        ktlint(ktlintVersion)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint(ktlintVersion)
    }
}
