package io.github.aanbrn.courses

import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CorsPropertiesTests {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `accepts http and https origins`() {
        val properties =
            CorsProperties().apply {
                allowedOrigins = listOf("http://localhost:3000", "https://example.com")
            }

        assertTrue(validator.validate(properties).isEmpty())
    }

    @Test
    fun `rejects a value that is not an http origin`() {
        val properties = CorsProperties().apply { allowedOrigins = listOf("not-an-origin") }

        assertTrue(validator.validate(properties).isNotEmpty())
    }

    @Test
    fun `treats blank entries as no origin`() {
        val properties = CorsProperties().apply { allowedOrigins = listOf("  ") }

        assertTrue(properties.allowedOrigins.isEmpty())
    }
}
