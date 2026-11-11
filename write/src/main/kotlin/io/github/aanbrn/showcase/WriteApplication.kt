package io.github.aanbrn.showcase

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WriteApplication

fun main(args: Array<String>) {
    runApplication<WriteApplication>(*args)
}
