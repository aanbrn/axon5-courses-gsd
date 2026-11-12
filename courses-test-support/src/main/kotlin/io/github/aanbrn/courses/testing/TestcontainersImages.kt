package io.github.aanbrn.courses.testing

/**
 * Docker images used by integration tests.
 *
 * The Gradle test task fills these system properties from `gradle/libs.versions.toml`; a test run
 * outside Gradle fails fast instead of silently using a stale tag.
 */
object TestcontainersImages {
    fun axonServer(): String = required("axonserver.image")

    fun postgres(): String = required("postgres.image")

    private fun required(property: String): String =
        requireNotNull(System.getProperty(property)) {
            "$property is set from gradle/libs.versions.toml by the Gradle test task"
        }
}
