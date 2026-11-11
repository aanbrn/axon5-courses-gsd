plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin { jvmToolchain(21) }

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

// The SPA is an api artifact: the Vite bundle ships inside the executable jar as static/.
tasks.named<ProcessResources>("processResources") {
    dependsOn(":frontend:assembleFrontend")
    from(project(":frontend").layout.projectDirectory.dir("dist")) { into("static") }
}

tasks.test { useJUnitPlatform { excludeTags("integration") } }

tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged 'integration'. Requires docker compose up -d."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.named("test"))
}
