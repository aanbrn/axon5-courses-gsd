package io.github.aanbrn.courses

import org.hibernate.validator.constraints.URL
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * CORS configuration for the split-origin frontend.
 *
 * An empty list keeps the gateway fail-closed: no cross-origin request is allowed until a
 * deployment names the browser origin.
 */
@Validated
@ConfigurationProperties("courses.cors")
class CorsProperties {
    var allowedOrigins: List<
        @URL(regexp = "^https?://.*")
        String,
    > = emptyList()
        set(value) {
            field = value.map(String::trim).filter(String::isNotEmpty)
        }
}
