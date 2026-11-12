package io.github.aanbrn.courses

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** HTTP gateway: sends commands, queries read models and serves the SPA; touches no SQL or events. */
@SpringBootApplication
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
