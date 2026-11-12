import org.gradle.accessors.dm.LibrariesForLibs

plugins { base }

val libs = the<LibrariesForLibs>()

// Stage the Procfile and start.sh into the built bundle so the Paketo nginx/procfile buildpacks see
// them in the build context (--path build/dist) while they remain source files in the module.
val stageImageFiles =
    tasks.register<Copy>("stageImageFiles") {
        description = "Stages Procfile and start.sh into the built bundle for the container image."
        dependsOn(
            tasks.matching { it.name == "assembleFrontend" || it.name == "generateBuildInfo" },
        )
        into(layout.buildDirectory.dir("dist"))
        from("Procfile", "start.sh")
    }

// A standalone nginx deployable built with Cloud Native Buildpacks: no Dockerfile is committed, so
// base-OS patches come from the Paketo run image.
tasks.register<PackBuildImageTask>("dockerBuildImage") {
    group = "build"
    description = "Builds the frontend container image with Paketo buildpacks."
    dependsOn(stageImageFiles)

    builder.set(
        "paketobuildpacks/builder-jammy-base:${libs.versions.paketo.builder.jammy.base.get()}",
    )
    buildpacks.set(
        listOf(
            "paketo-buildpacks/nginx@${libs.versions.paketo.nginx.get()}",
            "paketo-buildpacks/procfile@${libs.versions.paketo.procfile.get()}",
        ),
    )
    environment.putAll(
        mapOf(
            "BP_WEB_SERVER" to "nginx",
            "BP_WEB_SERVER_ROOT" to "/workspace",
        ),
    )
    appDir.set(layout.buildDirectory.dir("dist"))
    inputs.file("Procfile")
    inputs.file("start.sh")
}
