package io.github.aanbrn.showcase

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReadApplication

fun main(args: Array<String>) {
    runApplication<ReadApplication>(*args)
}
