package io.github.aanbrn.courses

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Command side: the only service that appends events; no datasource and no read models. */
@SpringBootApplication
class WriteApplication

fun main(args: Array<String>) {
    runApplication<WriteApplication>(*args)
}
