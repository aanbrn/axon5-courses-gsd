plugins { alias(libs.plugins.kotlin.jvm) }

val javaVersion = libs.versions.java.get()

kotlin { jvmToolchain(javaVersion.toInt()) }

dependencies {
    implementation(platform(project(":platform")))
    // Phase 3 adds axon-messaging/annotations and the message contract.
}
