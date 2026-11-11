plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    `jvm-test-suite`
}

val javaVersion = libs.versions.java.get()

kotlin { jvmToolchain(javaVersion.toInt()) }

dependencies {
    implementation(platform(project(":platform")))
    implementation(project(":domain"))
    implementation(libs.axoniq.spring.boot.starter)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.kotlin.reflect)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
    testImplementation("org.awaitility:awaitility")
}

// Only the executable jar is a build artifact; the plain jar would duplicate it.
tasks.jar { enabled = false }

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            dependencies {
                implementation(platform(project(":platform")))
                implementation(sourceSets.main.get().output)
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.awaitility:awaitility")
            }

            targets.all {
                testTask.configure { shouldRunAfter(tasks.named("test")) }
            }
        }
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.named("implementation").get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.named("runtimeOnly").get())
}
