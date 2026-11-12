import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Builds a container image from a built frontend with Cloud Native Buildpacks through the `pack` CLI.
 *
 * The module owns the image name; the convention plugin supplies the builder, the buildpacks and the
 * application directory. No Dockerfile is committed: the Paketo run image is patched upstream.
 */
abstract class PackBuildImageTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    /** The image name and tag; defaults to `${project.name}:${project.version}`. */
    @get:Input
    abstract val imageName: Property<String>

    // Captured at configuration time: Task.project is deprecated at execution time.
    private val projectDir = project.projectDir

    init {
        imageName.convention(project.provider { "${project.name}:${project.version}" })
    }

    /** Optional target platform, e.g. `linux/amd64` (passed through to `pack build --platform`). */
    @get:Input
    @get:Optional
    @get:Option(
        option = "imagePlatform",
        description = "The target platform (os/architecture/variant)",
    )
    abstract val imagePlatform: Property<String>

    /** The CNB builder image. */
    @get:Input
    abstract val builder: Property<String>

    /** The buildpacks to apply, pinned by version. */
    @get:Input
    abstract val buildpacks: ListProperty<String>

    /** Build-time and launch-time `BP_*` environment, passed as `--env KEY=VALUE`. */
    @get:Input
    abstract val environment: MapProperty<String, String>

    /** The built application directory passed to `pack build --path`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appDir: DirectoryProperty

    @TaskAction
    fun buildImage() {
        val packCli = packCli()
        if (packCli.isEmpty()) {
            throw GradleException(
                "The pack CLI is required to build the container image. Install it " +
                    "(https://buildpacks.io/docs) and make 'pack' available on PATH.",
            )
        }

        val args =
            mutableListOf(
                packCli,
                "build",
                imageName.get(),
                "--path",
                appDir.get().asFile.absolutePath,
                "--builder",
                builder.get(),
            )
        buildpacks.get().forEach { args += listOf("--buildpack", it) }
        environment.get().forEach { (name, value) -> args += listOf("--env", "$name=$value") }
        imagePlatform.orNull?.let { args += listOf("--platform", it) }

        execOperations.exec {
            commandLine(args)
            workingDir(projectDir)
        }
    }

    // Resolves pack to an absolute path from the real PATH: the daemon JVM caches PATH at start, so
    // a tool installed later would otherwise be invisible.
    private fun packCli(): String {
        val path = System.getenv("PATH") ?: return ""
        val executableNames =
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                listOf("pack.exe")
            } else {
                listOf("pack")
            }
        return path.split(File.pathSeparator).firstNotNullOfOrNull { dir ->
            executableNames
                .firstOrNull { name ->
                    val executable = File(dir, name)
                    executable.isFile && executable.canExecute()
                }?.let { File(dir, it).absolutePath }
        } ?: ""
    }
}
