package io.github.aanbrn.courses

import io.github.aanbrn.courses.testing.AxonServerRegistrationSupport
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [WriteApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class AxonServerRegistrationIT : AxonServerRegistrationSupport()
