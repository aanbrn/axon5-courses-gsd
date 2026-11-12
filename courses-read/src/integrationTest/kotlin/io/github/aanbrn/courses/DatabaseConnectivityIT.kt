package io.github.aanbrn.courses

import io.github.aanbrn.courses.testing.TestcontainersImages
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.Socket
import kotlin.test.assertTrue

/**
 * Proves that the pinned PostgreSQL image starts and accepts connections before the read model
 * depends on it. The assertion only needs TCP, so no JDBC driver is pulled into the module.
 */
@Testcontainers
class DatabaseConnectivityIT {
    @Test
    fun `PostgreSQL accepts TCP connections`() {
        Socket(postgres.host, postgres.getMappedPort(5432)).use { assertTrue(it.isConnected) }
    }

    companion object {
        @Container
        @JvmField
        val postgres: PostgreSQLContainer = PostgreSQLContainer(TestcontainersImages.postgres())
    }
}
