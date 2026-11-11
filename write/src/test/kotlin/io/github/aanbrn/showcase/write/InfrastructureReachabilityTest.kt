package io.github.aanbrn.showcase.write

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
class InfrastructureReachabilityTest {
    @Test
    fun `Axon Server answers its info endpoint`() {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:8024/actuator/info"))
                .GET()
                .build()
        val response =
            HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode())
    }

    @Test
    fun `PostgreSQL accepts TCP connections`() {
        Socket("localhost", 5432).use { assertTrue(it.isConnected) }
    }
}
