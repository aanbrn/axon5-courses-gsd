@file:Suppress("UnstableApiUsage") // jvm-test-suite APIs are still @Incubating

import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":courses-domain"))
}

// Container image for the Compose stack; the default <project>:<version> is local-only.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("aanbrn/axon5-courses-read:${project.version}")
    // In-network default; override AXON_SERVER per environment.
    environment.put("BPE_DEFAULT_AXON_SERVER", "axonserver:8124")
}

testing {
    suites {
        val test = suites.getByName<JvmTestSuite>("test")

        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project())
                implementation(project(":courses-test-support"))
                implementation(libs.testcontainers.postgresql)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}
