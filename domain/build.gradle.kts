plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(platform(project(":platform")))
    // Phase 3 adds axon-messaging/annotations and the message contract.
}
