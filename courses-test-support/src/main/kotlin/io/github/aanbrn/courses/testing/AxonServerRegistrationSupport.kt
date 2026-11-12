package io.github.aanbrn.courses.testing

import io.axoniq.framework.axonserver.connector.api.AxonServerConnectionManager
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Base for the per-service registration tests: starts one Axon Server container, points the service
 * at its mapped gRPC port, and asserts that the connection is established.
 *
 * Subclasses only add `@SpringBootTest(classes = [...])`. The container, the property source and
 * the assertion live here so the three services cannot drift.
 */
@Testcontainers
abstract class AxonServerRegistrationSupport {
    @Autowired
    lateinit var connectionManager: AxonServerConnectionManager

    @Suppress("FunctionName") // a backtick test name, resolved reflectively by JUnit
    @Test
    fun `service registers with Axon Server`() {
        await().atMost(Duration.ofSeconds(30)).until {
            connectionManager.connections()["default"] == true
        }
    }

    companion object {
        @Container
        @JvmField
        val axonServer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse(TestcontainersImages.axonServer()))
                .withExposedPorts(8024, 8124)
                .withEnv("axoniq_axonserver_standalone", "true")
                .waitingFor(Wait.forHttp("/actuator/info").forPort(8024))

        @Suppress("unused") // Spring resolves this reflectively through @DynamicPropertySource
        @JvmStatic
        @DynamicPropertySource
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") {
                "localhost:${axonServer.getMappedPort(8124)}"
            }
        }
    }
}
