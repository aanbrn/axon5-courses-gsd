@file:Suppress("UnstableApiUsage") // jvm-test-suite APIs are still @Incubating

// Conventions for a Spring Boot service module: Kotlin Spring, the Boot plugin, `jvm-test-suite`,
// the shared service dependencies and the common test-suite block. Docker image tags for the tests
// come from the version catalog and are passed as system properties.

import org.gradle.accessors.dm.LibrariesForLibs
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("jvm-test-suite")
}

val libs = the<LibrariesForLibs>()

// Docker image versions come from the catalog; the tests read these properties.
val axonServerImage = "axoniq/axonserver:${libs.versions.axonserver.get()}"
val postgresImage = "postgres:${libs.versions.postgres.get()}"

// A reproducible build-info file: the timestamp is the only non-deterministic entry.
springBoot {
    buildInfo {
        excludes.set(listOf("time"))
    }
}

// Every service image is a Paketo tiny JVM image with the health-checker binary, so Compose can
// probe /actuator/health without a shell or curl in the run image.
tasks.named<BootBuildImage>("bootBuildImage") {
    builder.set(
        "paketobuildpacks/builder-noble-java-tiny:${libs.versions.paketo.builder.noble.java.tiny.get()}",
    )
    buildpacks.addAll(
        listOf(
            "paketo-buildpacks/java",
            "paketo-buildpacks/spring-boot",
            "docker://paketobuildpacks/health-checker:${libs.versions.paketo.health.checker.get()}",
        ),
    )
    environment.putAll(
        mapOf(
            "BP_JVM_VERSION" to libs.versions.java.get(),
            "BP_HEALTH_CHECKER_ENABLED" to "true",
            // Every service listens on 8080 inside its container; only the host mapping differs.
            "BPE_DEFAULT_SERVER_PORT" to "8080",
        ),
    )
}

dependencies {
    implementation(libs.axoniq.spring.boot.starter)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.kotlin.reflect)
}

testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter()

            targets.all {
                testTask.configure {
                    systemProperty("axonserver.image", axonServerImage)
                    systemProperty("postgres.image", postgresImage)
                }
            }

            dependencies {
                implementation(platform(project(":courses-platform")))
                implementation(libs.spring.boot.starter.test)
                implementation(libs.kotlin.test)
                implementation(libs.awaitility)
            }
        }
    }
}
