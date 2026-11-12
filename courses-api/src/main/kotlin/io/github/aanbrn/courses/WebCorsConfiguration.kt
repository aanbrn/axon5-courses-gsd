package io.github.aanbrn.courses

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS for the standalone frontend, which is served from its own origin and calls this gateway
 * cross-origin. Fail-closed: with no configured origins no CORS mapping is registered.
 */
@Suppress("unused") // registered by component scanning
@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class WebCorsConfiguration(
    private val cors: CorsProperties,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        if (cors.allowedOrigins.isEmpty()) return

        registry
            .addMapping("/**")
            .allowedOrigins(*cors.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            // The SPA authenticates with a session cookie (ADR-0011); without this the browser drops
            // it on every cross-origin response.
            .allowCredentials(true)
    }
}
