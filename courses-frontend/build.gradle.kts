plugins {
    id("frontend-conventions")
}

description = "React SPA built with Vite and structured with Feature-Sliced Design"

// Deployable image name, overriding the PackBuildImageTask default (`<project>:<version>`).
// The API base URL is not baked in: it is provided at runtime via COURSES_API_BASE_URL (see start.sh).
tasks.named<PackBuildImageTask>("dockerBuildImage") {
    imageName.set("aanbrn/axon5-courses-frontend:${project.version}")
}

val npmInstall = tasks.register<Exec>("npmInstall") {
    description = "Installs the SPA dependencies from the committed lockfile"
    group = "build"
    workingDir = projectDir
    commandLine("npm", "ci")
    inputs.file("package.json")
    inputs.file("package-lock.json")
    outputs.dir("node_modules")
}

val assembleFrontend = tasks.register<Exec>("assembleFrontend") {
    description = "Type-checks and builds the SPA into dist/"
    group = "build"
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "build")
    inputs.dir("src")
    inputs.dir("public")
    inputs.file("index.html")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
    inputs.file("tsconfig.app.json")
    inputs.file("tsconfig.node.json")
    inputs.file("vite.config.ts")
    outputs.dir(layout.buildDirectory.dir("dist"))
}

// A deterministic equivalent of Spring Boot's build-info: name, group and version, no timestamp.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    description = "Writes a deterministic build-info.json into the SPA bundle"
    group = "build"
    dependsOn(assembleFrontend)

    val outputDir = layout.buildDirectory.dir("dist")
    val buildName = project.name
    val buildGroup = project.group.toString()
    val buildVersion = project.version.toString()
    outputs.file(outputDir.map { it.file("build-info.json") })

    doLast {
        val file = outputDir.get().file("build-info.json").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """{"name":"$buildName","group":"$buildGroup","version":"$buildVersion"}""" + "\n",
        )
    }
}

// The documented development loop: Vite on :5173 with the domain and session paths proxied to the gateway.
val dev = tasks.register<Exec>("dev") {
    description = "Runs the Vite dev server with the domain and session paths proxied to the gateway"
    group = "application"
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "dev")
}

val lintFsd = tasks.register<Exec>("lintFsd") {
    description = "Checks the Feature-Sliced Design boundaries (steiger + oxlint)"
    group = "verification"
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "lint:fsd")
    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("steiger.config.js")
    inputs.file(".oxlintrc.json")
}

val formatCheck = tasks.register<Exec>("formatCheck") {
    description = "Verifies the SPA is Prettier-formatted"
    group = "verification"
    dependsOn(npmInstall)
    workingDir = projectDir
    commandLine("npm", "run", "format:check")
    inputs.dir("public")
    inputs.files(
        "index.html",
        "package.json",
        "tsconfig.json",
        "tsconfig.app.json",
        "tsconfig.node.json",
        "vite.config.ts",
        "steiger.config.js",
        ".prettierignore",
    )
}

tasks.named("assemble") {
    dependsOn(generateBuildInfo)
}

tasks.named("check") {
    dependsOn(lintFsd, formatCheck)
}
