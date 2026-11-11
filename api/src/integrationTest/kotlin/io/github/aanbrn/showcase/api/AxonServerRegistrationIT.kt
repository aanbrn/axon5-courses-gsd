package io.github.aanbrn.showcase.api

import io.axoniq.framework.axonserver.connector.api.AxonServerConnectionManager
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest(
    classes = [io.github.aanbrn.showcase.ApiApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class AxonServerRegistrationIT {
    @Autowired
    lateinit var connectionManager: AxonServerConnectionManager

    @Test
    fun `api service registers with Axon Server`() {
        await().atMost(Duration.ofSeconds(30)).until {
            connectionManager.connections()["default"] == true
        }
    }
}
