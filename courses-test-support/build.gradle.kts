plugins {
    id("kotlin-conventions")
}

dependencies {
    api(libs.axoniq.spring.boot.starter)
    api(libs.spring.boot.starter.test)
    api(libs.awaitility)
    api(libs.testcontainers.junit.jupiter)
}
