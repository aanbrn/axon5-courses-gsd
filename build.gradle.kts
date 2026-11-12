import java.io.File

plugins {
    base
}

allprojects {
    group = "io.github.aanbrn.courses"
}

// The included build's sources are not part of the root build, so checking them is explicit.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":spotlessCheck"))
    dependsOn("checkComposeImages")
    dependsOn("lintWorkflows")
}

// Lints the GitHub Actions workflows. actionlint is a required local tool; CI installs a pinned
// release before the build, so the gate is enforced there.
val lintWorkflows =
    tasks.register<Exec>("lintWorkflows") {
        group = "verification"
        description = "Lints GitHub Actions workflows with actionlint."
        workingDir = layout.projectDirectory.asFile

        val workflows = fileTree(layout.projectDirectory.dir(".github/workflows")) { include("*.yml") }
        inputs.files(workflows)

        val actionlint = actionlintExecutable()

        doFirst {
            if (actionlint.isEmpty()) {
                throw GradleException(
                    "actionlint is required to lint GitHub workflows. Install it from " +
                        "https://github.com/rhysd/actionlint and put it on PATH.",
                )
            }
        }

        commandLine(actionlint, *workflows.files.map { it.absolutePath }.toTypedArray())
    }

// Resolves actionlint to an absolute path from the real PATH: the daemon JVM caches PATH at start,
// so a tool installed later would otherwise be invisible.
fun actionlintExecutable(): String {
    val path = System.getenv("PATH") ?: return ""
    val names =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            listOf("actionlint.exe", "actionlint.bat", "actionlint.cmd")
        } else {
            listOf("actionlint")
        }
    return path.split(File.pathSeparator).firstNotNullOfOrNull { dir ->
        names
            .firstOrNull { name ->
                val candidate = File(dir, name)
                candidate.isFile && candidate.canExecute()
            }?.let { File(dir, it).absolutePath }
    } ?: ""
}

// Guards the deployable wiring: project images must follow the project version through
// `${PROJECT_VERSION:-<version>}`, and infrastructure images must carry a pinned tag that matches
// the version catalog.
val checkComposeImages =
    tasks.register("checkComposeImages") {
        group = "verification"
        description = "Checks that Compose image tags are pinned and match the project version and catalog."

        val composeFile = layout.projectDirectory.file("compose.yaml")
        val projectVersion = version.toString()
        val axonServerVersion = libs.versions.axonserver.get()
        val postgresVersion = libs.versions.postgres.get()

        inputs.file(composeFile)
        inputs.property("projectVersion", projectVersion)
        inputs.property("axonserver", axonServerVersion)
        inputs.property("postgres", postgresVersion)

        doLast {
            val imageLine = Regex("""^\s*image:\s*(\S+)\s*$""")
            val projectImage = Regex(
                "^aanbrn/axon5-courses-[a-z-]+:\\$\\{PROJECT_VERSION:-([^}]+)}$",
            )
            val violations = mutableListOf<String>()

            composeFile.asFile.readLines().forEachIndexed { index, line ->
                val image = imageLine.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
                val lineNumber = index + 1

                if (image.startsWith("aanbrn/axon5-courses-")) {
                    val match = projectImage.matchEntire(image)
                    if (match == null) {
                        violations +=
                            "compose.yaml:$lineNumber: project image must use " +
                                "${'$'}{PROJECT_VERSION:-$projectVersion}, got $image"
                    } else if (match.groupValues[1] != projectVersion) {
                        violations +=
                            "compose.yaml:$lineNumber: version default ${match.groupValues[1]} " +
                                "does not match the project version $projectVersion"
                    }
                    return@forEachIndexed
                }

                val repository = image.substringBefore(':')
                val tag = image.substringAfter(':', missingDelimiterValue = "")
                if (tag.isEmpty() || tag == "latest") {
                    violations +=
                        "compose.yaml:$lineNumber: infrastructure image must carry a pinned tag, got $image"
                    return@forEachIndexed
                }

                val expected =
                    when (repository) {
                        "axoniq/axonserver" -> axonServerVersion
                        "postgres" -> postgresVersion
                        else -> return@forEachIndexed
                    }
                val variableTag = Regex("^\\$\\{([A-Z_]+):-([^}]+)}$")
                val actual = variableTag.matchEntire(tag)?.groupValues?.get(2) ?: tag
                if (actual != expected) {
                    violations +=
                        "compose.yaml:$lineNumber: $repository tag $actual does not match the catalog version $expected"
                }
            }

            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Compose image check failed:\n" + violations.joinToString("\n") { "  - $it" },
                )
            }
        }
    }
