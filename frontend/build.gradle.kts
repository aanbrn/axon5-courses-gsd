plugins {
    `base`
}

description = "React SPA built with Vite and structured with Feature-Sliced Design"

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
    inputs.file("index.html")
    inputs.file("package.json")
    inputs.file("tsconfig.json")
    inputs.file("tsconfig.app.json")
    inputs.file("tsconfig.node.json")
    inputs.file("vite.config.ts")
    outputs.dir("dist")
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
    dependsOn(assembleFrontend)
}

tasks.named("check") {
    dependsOn(lintFsd, formatCheck)
}
