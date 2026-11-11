package io.github.aanbrn.showcase.read

import io.axoniq.framework.axonserver.connector.api.AxonServerConnectionManager
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@Tag("integration")
@SpringBootTest(
    classes = [io.github.aanbrn.showcase.ReadApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class AxonServerRegistrationIT {
    @Autowired
    lateinit var connectionManager: AxonServerConnectionManager

    @Test
    fun `read service registers with Axon Server`() {
        await().atMost(Duration.ofSeconds(30)).until {
            connectionManager.connections()["default"] == true
        }
    }
}
