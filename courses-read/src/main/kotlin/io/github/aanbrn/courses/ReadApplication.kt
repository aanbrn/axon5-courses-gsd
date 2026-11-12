package io.github.aanbrn.courses

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Read side: projects events into PostgreSQL and answers queries; owns the read model. */
@SpringBootApplication
class ReadApplication

fun main(args: Array<String>) {
    runApplication<ReadApplication>(*args)
}
