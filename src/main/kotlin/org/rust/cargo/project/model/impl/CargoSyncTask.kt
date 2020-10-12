/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.project.model.impl

import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildDescriptor
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.exists
import org.rust.RsTask
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.toolchain.rustup
import org.rust.cargo.toolchain.tools.cargoOrWrapper
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.util.DownloadResult
import org.rust.openapiext.TaskResult
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

class CargoSyncTask(
    project: Project,
    private val cargoProjects: List<CargoProjectImpl>,
    private val result: CompletableFuture<List<CargoProjectImpl>>
) : Task.Backgroundable(project, "Reloading Cargo projects", true), RsTask {

    override val taskType: RsTask.TaskType
        get() = RsTask.TaskType.CARGO_SYNC

    override val runSyncInUnitTests: Boolean
        get() = true

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        val syncProgress = SyncViewManager.createBuildProgress(project)

        val refreshedProjects = try {
            syncProgress.start(createSyncProgressDescriptor())
            val refreshedProjects = doRun(indicator, syncProgress)
            val isUpdateFailed = refreshedProjects.any { it.mergedStatus is CargoProject.UpdateStatus.UpdateFailed }
            if (isUpdateFailed) {
                syncProgress.fail()
            } else {
                syncProgress.finish()
            }
            refreshedProjects
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                syncProgress.cancel()
            } else {
                syncProgress.fail()
            }
            result.completeExceptionally(e)
            throw e
        }
        result.complete(refreshedProjects)
    }

    private fun doRun(
        indicator: ProgressIndicator,
        syncProgress: BuildProgress<BuildProgressDescriptor>
    ): List<CargoProjectImpl> {
        val toolchain = project.toolchain
        val refreshedProjects = if (toolchain == null) {
            syncProgress.fail(System.currentTimeMillis(), "Cargo project update failed:\nNo Rust toolchain")
            cargoProjects
        } else {
            cargoProjects.map { cargoProject ->
                syncProgress.runWithChildProgress(
                    "Sync ${cargoProject.presentableName} project",
                    createContext = { it },
                    action = { childProgress ->
                        if (!cargoProject.workingDirectory.exists()) {
                            cargoProject.copy(
                                stdlibStatus = CargoProject.UpdateStatus.UpdateFailed("Project directory does not exist")
                            )
                        } else {
                            val context = SyncContext(project, cargoProject, toolchain, indicator, childProgress)
                            cargoProject.withRustcInfo(fetchRustcInfo(context))
                                .withWorkspace(fetchCargoWorkspace(context))
                                .withStdlib(fetchStdlib(context))
                        }
                    }
                )
            }
        }

        return refreshedProjects
    }

    private fun createSyncProgressDescriptor(): BuildProgressDescriptor {
        val buildContentDescriptor = BuildContentDescriptor(null, null, object : JComponent() {}, "Cargo")
        buildContentDescriptor.isActivateToolWindowWhenFailed = true
        buildContentDescriptor.isActivateToolWindowWhenAdded = false
        val descriptor = DefaultBuildDescriptor("Cargo", "Cargo", project.basePath!!, System.currentTimeMillis())
            .withContentDescriptor { buildContentDescriptor }
        return object : BuildProgressDescriptor {
            override fun getTitle(): String = descriptor.title
            override fun getBuildDescriptor(): BuildDescriptor = descriptor
        }
    }

    data class SyncContext(
        val project: Project,
        val oldCargoProject: CargoProjectImpl,
        val toolchain: RsToolchain,
        val progress: ProgressIndicator,
        private val syncProgress: BuildProgress<BuildProgressDescriptor>
    ) {
        fun <T> runWithChildProgress(
            title: String,
            action: (SyncContext) -> TaskResult<T>
        ): TaskResult<T> {
            progress.checkCanceled()
            progress.text = title

            return syncProgress.runWithChildProgress(title, { copy(syncProgress = it) }, action) { childProgress, result ->
                when (result) {
                    is TaskResult.Ok -> childProgress.finish()
                    is TaskResult.Err -> {
                        childProgress.message(result.reason, result.message.orEmpty(), MessageEvent.Kind.ERROR, null)
                        childProgress.fail()
                    }
                }
            }
        }

        fun withProgressText(text: String) {
            progress.text = text
            syncProgress.progress(text)
        }
    }
}

private fun fetchRustcInfo(context: CargoSyncTask.SyncContext): TaskResult<RustcInfo> {
    return context.runWithChildProgress("Getting toolchain version") { childContext ->
        if (!childContext.toolchain.looksLikeValidToolchain()) {
            return@runWithChildProgress TaskResult.Err("Invalid Rust toolchain ${childContext.toolchain.presentableLocation}")
        }

        val sysroot = childContext.toolchain.rustc().getSysroot(childContext.oldCargoProject.workingDirectory)
            ?: return@runWithChildProgress TaskResult.Err("failed to get project sysroot")

        val rustcVersion = childContext.toolchain.rustc().queryVersion()

        TaskResult.Ok(RustcInfo(sysroot, rustcVersion))
    }
}

private fun fetchCargoWorkspace(context: CargoSyncTask.SyncContext): TaskResult<CargoWorkspace> {
    return context.runWithChildProgress("Updating workspace info") { childContext ->

        val toolchain = childContext.toolchain
        if (!toolchain.looksLikeValidToolchain()) {
            return@runWithChildProgress TaskResult.Err("Invalid Rust toolchain ${toolchain.presentableLocation}")
        }
        val projectDirectory = childContext.oldCargoProject.workingDirectory
        val cargo = toolchain.cargoOrWrapper(projectDirectory)
        try {
            val projectDescriptionData = cargo.fullProjectDescription(childContext.project, projectDirectory, object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                    val text = event.text.trim { it <= ' ' }
                    if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                        childContext.withProgressText(text)
                    }

                }
            })
            val manifestPath = projectDirectory.resolve("Cargo.toml")

            // Running "cargo rustc -- --print cfg" causes an error when run in a project with multiple targets
            // error: extra arguments to `rustc` can only be passed to one target, consider filtering
            // the package by passing e.g. `--lib` or `--bin NAME` to specify a single target
            // Running "cargo rustc --bin=projectname  -- --print cfg" we can get around this
            // but it also compiles the whole project, which is probably not wanted
            // TODO: This does not query the target specific cfg flags during cross compilation :-(
            val rawCfgOptions = toolchain.rustc().getCfgOptions(projectDirectory) ?: emptyList()
            val cfgOptions = CfgOptions.parse(rawCfgOptions)
            val ws = CargoWorkspace.deserialize(manifestPath, projectDescriptionData, cfgOptions)
            TaskResult.Ok(ws)
        } catch (e: ExecutionException) {
            TaskResult.Err("Failed to run Cargo", e.message)
        }
    }
}

private fun fetchStdlib(context: CargoSyncTask.SyncContext): TaskResult<StandardLibrary> {
    return context.runWithChildProgress("Getting Rust stdlib") { childContext ->

        val workingDirectory = childContext.oldCargoProject.workingDirectory
        if (childContext.oldCargoProject.doesProjectLooksLikeRustc()) {
            // rust-lang/rust contains stdlib inside the project
            val std = StandardLibrary.fromPath(workingDirectory.toString())
                ?.asPartOfCargoProject()
            if (std != null) {
                return@runWithChildProgress TaskResult.Ok(std)
            }
        }

        val rustup = childContext.toolchain.rustup(workingDirectory)
        if (rustup == null) {
            val explicitPath = childContext.project.rustSettings.explicitPathToStdlib
                ?: childContext.toolchain.rustc().getStdlibFromSysroot(workingDirectory)?.path
            val lib = explicitPath?.let { StandardLibrary.fromPath(it) }
            return@runWithChildProgress when {
                explicitPath == null -> TaskResult.Err("no explicit stdlib or rustup found")
                lib == null -> TaskResult.Err("invalid standard library: $explicitPath")
                else -> TaskResult.Ok(lib)
            }
        }

        rustup.fetchStdlib()
    }
}


private fun Rustup.fetchStdlib(): TaskResult<StandardLibrary> {
    return when (val download = downloadStdlib()) {
        is DownloadResult.Ok -> {
            val lib = StandardLibrary.fromFile(download.value)
            if (lib == null) {
                TaskResult.Err("Corrupted standard library: ${download.value.presentableUrl}")
            } else {
                TaskResult.Ok(lib)
            }
        }
        is DownloadResult.Err -> TaskResult.Err("Download failed: ${download.error}")
    }
}

private fun <T, R> BuildProgress<BuildProgressDescriptor>.runWithChildProgress(
    title: String,
    createContext: (BuildProgress<BuildProgressDescriptor>) -> T,
    action: (T) -> R,
    onResult: (BuildProgress<BuildProgressDescriptor>, R) -> Unit = { progress, _ -> progress.finish() }
): R {
    val childProgress = startChildProgress(title)
    try {
        val context = createContext(childProgress)
        val result = action(context)
        onResult(childProgress, result)
        return result
    } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
            cancel()
        } else {
            fail()
        }
        throw e
    }
}
